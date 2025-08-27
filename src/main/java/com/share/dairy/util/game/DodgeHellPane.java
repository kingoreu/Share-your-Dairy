package com.share.dairy.util.game;

import javafx.animation.AnimationTimer;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.text.Text;

import java.util.*;

/**
 * 오버레이에 삽입해 쓰는 JavaFX Pane 컴포넌트 버전의 장애물 피하기 게임.
 * - new DodgeHellPane(width, height)
 * - start() / stop()
 * - requestGameFocus()
 * - setProgressTint(int), setDifficultyScale(double)
 */
public class DodgeHellPane extends Pane {

    // 화면
    private final int W, H;

    // 플레이어(히트박스) + 입력
    private final Circle player = new Circle(8, Color.AQUA);
    private boolean up, down, left, right, dashPressed, slowPressed;
    private double baseSpeed = 3.1, dashSpeed = 8.0;
    private boolean dashing = false;
    private double dashTime = 0, dashIFrame = 0;
    private final double DASH_DURATION = 0.22, IFRAME_DURATION = 0.25, DASH_COOLDOWN = 0.9;
    private double dashCooldown = 0;

    // 슬로모(게이지)
    private double slowGauge = 100; // 0~100
    private final double SLOW_CONSUME = 35.0; // /sec
    private final double SLOW_RECOVER = 18.0; // /sec
    private final double SLOW_FACTOR = 0.55;

    // 장애물
    static class Bullet {
        Circle sprite;
        double vx, vy; // px per frame at 60fps 기준, dt 보정 사용
        boolean harmful = true;
    }
    static class Telegraph {
        Line line;
        double ttl; // seconds until fire
        Runnable onFire;
    }
    private final List<Bullet> bullets = new ArrayList<>();
    private final List<Telegraph> telegraphs = new ArrayList<>();
    private final Random rnd = new Random();

    // 패턴/라운드
    private int level = 1;
    private double levelTime = 0;
    private double timeScale = 1.0; // 슬로모 적용
    private double diffMul = 1.0;   // 레벨 상승 시 증가
    private double survival = 0;    // 생존 시간 = 점수
    private int lives = 3;

    // UI
    private final Text hud = new Text();

    // 타이머
    private AnimationTimer loop;
    private long lastNs = 0;

    // 진행률 틴트/난이도 스케일 외부 제어
    private double progressTint = 0.0; // 0~1
    private double difficultyScale = 1.0; // 0.5~2.0

    // 스폰
    private double spawnTimer = 0;

    public DodgeHellPane(double width, double height) {
        this.W = (int) Math.round(width);
        this.H = (int) Math.round(height);

        setPrefSize(W, H);
        setMinSize(W, H);
        setMaxSize(W, H);
        setStyle("-fx-background-color: linear-gradient(#0f1020, #151a2e); -fx-background-radius: 16;");

        // 플레이어 초기 위치
        player.setTranslateX(W / 2.0);
        player.setTranslateY(H - 120);
        player.setStroke(Color.WHITE);
        player.setStrokeWidth(1.2);

        hud.setFill(Color.WHITE);
        hud.setTranslateX(10);
        hud.setTranslateY(22);

        getChildren().addAll(player, hud);

        // 키 입력 (Pane가 포커스 받도록)
        setFocusTraversable(true);
        setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.W || e.getCode() == KeyCode.UP)    up = true;
            if (e.getCode() == KeyCode.S || e.getCode() == KeyCode.DOWN)  down = true;
            if (e.getCode() == KeyCode.A || e.getCode() == KeyCode.LEFT)  left = true;
            if (e.getCode() == KeyCode.D || e.getCode() == KeyCode.RIGHT) right = true;
            if (e.getCode() == KeyCode.SPACE)  dashPressed = true;  // 대시
            if (e.getCode() == KeyCode.SHIFT)  slowPressed = true;  // 슬로모
        });
        setOnKeyReleased(e -> {
            if (e.getCode() == KeyCode.W || e.getCode() == KeyCode.UP)    up = false;
            if (e.getCode() == KeyCode.S || e.getCode() == KeyCode.DOWN)  down = false;
            if (e.getCode() == KeyCode.A || e.getCode() == KeyCode.LEFT)  left = false;
            if (e.getCode() == KeyCode.D || e.getCode() == KeyCode.RIGHT) right = false;
            if (e.getCode() == KeyCode.SPACE) dashPressed = false;
            if (e.getCode() == KeyCode.SHIFT) slowPressed = false;
        });
    }

    /** 오버레이에서 호출: 게임 루프 시작 */
    public void start() {
        if (loop != null) return;
        loop = new AnimationTimer() {
            @Override public void handle(long now) {
                if (lastNs == 0) { lastNs = now; return; }
                double dt = (now - lastNs) / 1_000_000_000.0; // seconds
                lastNs = now;

                // 외부 난이도 스케일 적용(탄속/패턴 배율)
                diffMul = 1.0 * difficultyScale;

                // 슬로모 스케일
                boolean slow = slowPressed && slowGauge > 0.1;
                timeScale = slow ? SLOW_FACTOR : 1.0;
                double sdt = dt * timeScale;

                updatePlayer(sdt);
                updateTelegraphs(sdt);
                updateBullets(sdt);

                survival += sdt;
                levelTime += sdt;
                difficultyRamp();

                spawnPatternScheduler(sdt);

                updateUI();

                if (lives <= 0) {
                    hud.setText("GAME OVER  |  SURVIVED " + (int)survival + "s");
                    stop(); // AnimationTimer stop
                }
            }
        };
        loop.start();
    }

    /** 오버레이 닫힐 때 호출: 루프 정지/정리 */
    public void stop() {
        if (loop != null) {
            loop.stop();
            loop = null;
        }
    }

    /** 키 입력 받도록 */
    public void requestGameFocus() { requestFocus(); }

    /** 진행률(0~100)에 따라 배경을 조금씩 밝게 */
    public void setProgressTint(int percent) {
        double p = Math.max(0, Math.min(100, percent)) / 100.0;
        this.progressTint = p;
        // 배경 색상 보정
        double a = 0.00 + 0.25 * p;
        double b = 0.10 + 0.35 * p;
        Color c1 = Color.color(0.06 + a, 0.07 + a, 0.16 + a);
        Color c2 = Color.color(0.10 + b, 0.12 + b, 0.26 + b);
        setStyle("-fx-background-color: linear-gradient(" + toRgb(c1) + "," + toRgb(c2) + "); -fx-background-radius: 16;");
    }

    /** 외부 난이도 배율(0.5~2.0 권장) */
    public void setDifficultyScale(double scale) {
        difficultyScale = Math.max(0.5, Math.min(2.0, scale));
    }

    // ===== 게임 로직 =====
    private void updatePlayer(double dt) {
        // 대시/무적
        if (dashCooldown > 0) dashCooldown -= dt;
        if (dashing) { dashTime -= dt; if (dashTime <= 0) dashing = false; }
        if (dashIFrame > 0) dashIFrame -= dt;

        double spd = baseSpeed * (slowPressed && slowGauge > 0 ? timeScale : 1.0);
        if (dashPressed && dashCooldown <= 0) {
            dashing = true;
            dashTime = DASH_DURATION;
            dashIFrame = IFRAME_DURATION;
            dashCooldown = DASH_COOLDOWN;
        }
        if (dashing) spd = dashSpeed;

        double vx = 0, vy = 0;
        if (left) vx -= spd;
        if (right) vx += spd;
        if (up) vy -= spd;
        if (down) vy += spd;

        player.setTranslateX(clamp(player.getTranslateX() + vx, 10, W - 10));
        player.setTranslateY(clamp(player.getTranslateY() + vy, 10, H - 10));

        // 슬로모 게이지
        if (slowPressed) slowGauge = Math.max(0, slowGauge - SLOW_CONSUME * dt);
        else slowGauge = Math.min(100, slowGauge + SLOW_RECOVER * dt);
    }

    private void updateTelegraphs(double dt) {
        List<Telegraph> done = new ArrayList<>();
        for (Telegraph t : telegraphs) {
            t.ttl -= dt;
            if (t.ttl <= 0) {
                t.onFire.run();
                getChildren().remove(t.line);
                done.add(t);
            } else {
                double alpha = 0.4 + 0.6 * Math.abs(Math.sin(t.ttl * 10));
                t.line.setStroke(Color.color(1, 0.3, 0.3, alpha));
            }
        }
        telegraphs.removeAll(done);
    }

    private void updateBullets(double dt) {
        List<Bullet> out = new ArrayList<>();
        for (Bullet b : bullets) {
            b.sprite.setTranslateX(b.sprite.getTranslateX() + b.vx * 60 * dt);
            b.sprite.setTranslateY(b.sprite.getTranslateY() + b.vy * 60 * dt);

            if (b.harmful && dashIFrame <= 0 && collides(player, b.sprite)) {
                lives--;
                b.harmful = false;
                b.sprite.setFill(Color.GRAY);
            }

            double x = b.sprite.getTranslateX(), y = b.sprite.getTranslateY();
            if (x < -40 || x > W + 40 || y < -40 || y > H + 40) {
                out.add(b); getChildren().remove(b.sprite);
            }
        }
        bullets.removeAll(out);
    }

    // ===== 난이도/스폰 =====
    private void spawnPatternScheduler(double dt) {
        spawnTimer -= dt;
        if (spawnTimer <= 0) {
            PatternType p = choosePattern();
            firePattern(p);
            spawnTimer = Math.max(0.45, 1.4 - 0.08 * level);
        }
    }

    private enum PatternType { SWEEP_LEFT, SWEEP_RIGHT, FAN, BURST, RAIN }

    private PatternType choosePattern() {
        double r = rnd.nextDouble();
        if (r < 0.25) return PatternType.SWEEP_LEFT;
        if (r < 0.50) return PatternType.SWEEP_RIGHT;
        if (r < 0.70) return PatternType.FAN;
        if (r < 0.88) return PatternType.RAIN;
        return PatternType.BURST;
    }

    private void firePattern(PatternType type) {
        switch (type) {
            case SWEEP_LEFT -> sweep(false);
            case SWEEP_RIGHT -> sweep(true);
            case FAN -> fan();
            case RAIN -> rain();
            case BURST -> burst();
        }
    }

    private void sweep(boolean rightward) {
        double y = 40 + rnd.nextInt(Math.max(60, H - 180));
        Line line = new Line(0, y, W, y);
        line.setStrokeWidth(6);
        line.setStroke(Color.color(1, 0.2, 0.2, 0.6));
        getChildren().add(line);

        Telegraph t = new Telegraph();
        t.line = line; t.ttl = 0.6;
        t.onFire = () -> {
            int n = Math.max(6, 9 + level);
            int safeIndex = rnd.nextInt(n);
            for (int i = 0; i < n; i++) {
                if (i == safeIndex) continue; // 한 줄은 비워서 통로 보장
                double x = (i + 0.5) * (W / (double)n);
                spawnBullet(x, y, rightward ? 1.6 + 0.08 * level : -1.6 - 0.08 * level, 0,
                        8, Color.ORANGE);
            }
        };
        telegraphs.add(t);
    }

    private void fan() {
        double cx = rnd.nextBoolean() ? 80 : W - 80;
        double cy = 60 + rnd.nextInt(Math.max(40, H - 200));
        Line line = new Line(cx, cy, cx + 1, cy + 1);
        line.setStrokeWidth(14);
        line.setStroke(Color.color(1, 0.4, 0.2, 0.7));
        getChildren().add(line);

        Telegraph t = new Telegraph();
        t.line = line; t.ttl = 0.55;
        t.onFire = () -> {
            int n = 8 + level;
            double speed = 1.5 + 0.1 * level;
            double base = (player.getTranslateY() < cy ? Math.PI/6 : -Math.PI/6);
            for (int i = 0; i < n; i++) {
                double ang = base + (i - n/2.0) * (Math.PI / (n/2.0 + 2));
                spawnBullet(cx, cy, Math.cos(ang) * speed, Math.sin(ang) * speed,
                        6, Color.GOLD);
            }
        };
        telegraphs.add(t);
    }

    private void rain() {
        for (int i = 0; i < 6 + level; i++) {
            double x = 20 + rnd.nextInt(W - 40);
            double delay = 0.08 * i;
            scheduleBullet(x, -20, 0, 2.0 + 0.09 * level, 7, Color.SKYBLUE, delay);
        }
    }

    private void burst() {
        double cx = W/2.0 + rnd.nextGaussian()*40;
        double cy = H/2.0 + rnd.nextGaussian()*40;
        int n = 12 + level;
        double speed = 1.9 + 0.08 * level;
        for (int i = 0; i < n; i++) {
            double ang = (2*Math.PI * i)/n;
            spawnBullet(cx, cy, Math.cos(ang)*speed, Math.sin(ang)*speed, 5, Color.CRIMSON);
        }
    }

    // ===== 유틸 =====
    private void spawnBullet(double x, double y, double vx, double vy, double r, Color color) {
        Circle c = new Circle(r, color);
        c.setTranslateX(x); c.setTranslateY(y);
        Bullet b = new Bullet(); b.sprite = c; b.vx = vx * diffMul; b.vy = vy * diffMul;
        bullets.add(b);
        getChildren().add(c);
    }

    private void scheduleBullet(double x, double y, double vx, double vy, double r, Color color, double delay) {
        Line dot = new Line(x, y, x+1, y+1);
        dot.setStrokeWidth(6);
        dot.setStroke(Color.color(0.7, 0.9, 1.0, 0.6));
        getChildren().add(dot);

        Telegraph t = new Telegraph();
        t.line = dot; t.ttl = Math.max(0.2, delay);
        t.onFire = () -> spawnBullet(x, y, vx, vy, r, color);
        telegraphs.add(t);
    }

    private boolean collides(Circle a, Circle b) {
        double dx = a.getTranslateX() - b.getTranslateX();
        double dy = a.getTranslateY() - b.getTranslateY();
        double rr = (a.getRadius()) + (b.getRadius());
        return dx*dx + dy*dy <= rr*rr;
    }

    private double clamp(double v, double lo, double hi) { return Math.max(lo, Math.min(hi, v)); }

    private void difficultyRamp() {
        if (levelTime >= 12) {
            level++; levelTime = 0;
            // diffMul은 외부 difficultyScale과 별개로 패턴 내부 가중치에만 소폭 반영
            // 시각 피드백
            setProgressTint((int)(progressTint*100)); // 배경 유지
        }
    }

    private void updateUI() {
        hud.setText(String.format(
                "LV %d  HP %d  %.0fs  Slow %.0f%%  DashCD %.1fs",
                level, lives, survival, slowGauge, Math.max(0, dashCooldown)
        ));
        if (dashIFrame > 0) player.setFill(Color.LIGHTGREEN);
        else if (dashing)   player.setFill(Color.LIGHTYELLOW);
        else                player.setFill(Color.AQUA);
    }

    private String toRgb(Color c) {
        int r=(int)(c.getRed()*255), g=(int)(c.getGreen()*255), b=(int)(c.getBlue()*255);
        return "rgb(" + r + "," + g + "," + b + ")";
    }
}

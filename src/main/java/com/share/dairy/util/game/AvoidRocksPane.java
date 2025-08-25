package com.share.dairy.util.game;

import javafx.animation.AnimationTimer;
import javafx.geometry.Insets;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * AvoidRocksPane
 * ------------------------------------------
 * - JavaFX Canvas 기반의 경량 미니게임 컴포넌트.
 * - 하늘에서 떨어지는 돌(원형)을 ←/→ 로 피한다.
 * - 로딩 화면과 함께 띄워 UX를 재미있게 만든다.
 *
 * 외부 API
 *  - requestGameFocus(): 키 입력 포커스 강제 획득
 *  - setProgressTint(int percent): 진행률(0~100)에 따라 배경에 햇빛 틴트를 더한다
 *  - stop(): 오버레이 닫힐 때 타이머 정지(메모리 릭 방지)
 *
 * 의존성: JavaFX만 사용. 타 라이브러리 없음.
 * 스레드: JavaFX Application Thread에서만 생성/제거/그리기.
 */
public final class AvoidRocksPane extends StackPane {

    // ====== 렌더링 기본 ======
    private final Canvas canvas = new Canvas();
    private final GraphicsContext g;
    private double W, H;

    // ====== 플레이어(바닥 막대) ======
    private double px, py;            // 좌상단
    private final double pw = 46;     // 가로
    private final double ph = 18;     // 세로
    private double vel = 0;           // -1(좌), 0(정지), +1(우)
    private final double speed = 240; // px/sec

    // ====== 돌(낙하 물체) ======
    private static final class Rock {
        double x, y;   // 중심 좌표
        double r;      // 반경
        double vy;     // 낙하 속도(px/sec)
    }
    private final List<Rock> rocks = new ArrayList<>();
    private final Random rand = new Random();
    private double spawnTimer = 0;

    // ====== 연출 ======
    private double flash = 0; // 충돌 시 화면 붉은 플래시(0~1)
    private double tint  = 0; // 진행률 기반 햇빛 틴트(0~1)

    // ====== 루프 ======
    private AnimationTimer loop;
    private long lastNs;

    /**
     * @param width  게임 영역 가로(px)
     * @param height 게임 영역 세로(px)
     */
    public AvoidRocksPane(double width, double height) {
        setPadding(new Insets(12));
        setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        setStyle("-fx-background-color: rgba(255,255,255,0.08); -fx-background-radius: 12;");

        this.W = width;
        this.H = height;

        canvas.setWidth(W);
        canvas.setHeight(H);
        getChildren().add(canvas);
        this.g = canvas.getGraphicsContext2D();

        // 플레이어 초기 위치(바닥 중앙)
        this.px = W / 2.0 - pw / 2.0;
        this.py = H - 28;

        // 키 입력: ← / →
        setFocusTraversable(true);
        setOnKeyPressed(ev -> {
            if (ev.getCode() == KeyCode.LEFT)  vel = -1;
            if (ev.getCode() == KeyCode.RIGHT) vel = +1;
        });
        setOnKeyReleased(ev -> {
            if ((ev.getCode() == KeyCode.LEFT  && vel < 0) ||
                    (ev.getCode() == KeyCode.RIGHT && vel > 0)) {
                vel = 0;
            }
        });

        // 메인 루프 시작
        loop = new AnimationTimer() {
            @Override public void handle(long now) {
                if (lastNs == 0) { lastNs = now; draw(); return; }
                double dt = (now - lastNs) / 1_000_000_000.0; // ns → sec
                lastNs = now;
                update(dt);
                draw();
            }
        };
        loop.start();
    }

    /** 외부에서 포커스 강제 */
    public void requestGameFocus() { requestFocus(); }

    /** 진행률(0~100)에 따라 상단 틴트(햇빛 느낌) 강도 조절 */
    public void setProgressTint(int percent) {
        if (percent < 0) return;
        this.tint = Math.min(1.0, percent / 100.0);
    }

    /** 오버레이 닫힐 때 반드시 호출해서 타이머 정지 */
    public void stop() {
        if (loop != null) {
            loop.stop();
            loop = null;
        }
    }

    // ====== 내부 로직 ======
    private void update(double dt) {
        // 플레이어 이동
        px += vel * speed * dt;
        px = Math.max(6, Math.min(px, W - pw - 6));

        // 돌 스폰(0.45~0.95초 간격)
        spawnTimer -= dt;
        if (spawnTimer <= 0) {
            spawnTimer = 0.45 + rand.nextDouble() * 0.5;
            Rock r = new Rock();
            r.r  = 8 + rand.nextDouble() * 14;
            r.x  = r.r + rand.nextDouble() * (W - r.r * 2);
            r.y  = -r.r - 4;
            r.vy = 180 + rand.nextDouble() * 160;
            rocks.add(r);
        }

        // 돌 이동 + 화면 밖 제거
        for (int i = rocks.size() - 1; i >= 0; i--) {
            Rock r = rocks.get(i);
            r.y += r.vy * dt;
            if (r.y - r.r > H) rocks.remove(i);
        }

        // 충돌 체크 (원 vs AABB 근사)
        for (Rock r : rocks) {
            double cx = clamp(r.x, px, px + pw);
            double cy = clamp(r.y, py, py + ph);
            double dx = r.x - cx;
            double dy = r.y - cy;
            if (dx * dx + dy * dy <= r.r * r.r) {
                flash = 1.0; // 충돌 플래시 트리거
            }
        }

        // 플래시 감쇠
        if (flash > 0) flash = Math.max(0, flash - dt * 2.5);
    }

    private void draw() {
        // 배경
        g.setFill(Color.rgb(18, 20, 25));
        g.fillRect(0, 0, W, H);

        // 진행률 틴트(상단 햇살 느낌)
        if (tint > 0) {
            g.setFill(Color.rgb(255, 235, 130, 0.15 * tint));
            g.fillRect(0, 0, W, H * 0.6);
        }

        // 플레이어
        g.setFill(Color.rgb(200, 240, 255));
        g.fillRoundRect(px, py, pw, ph, 8, 8);

        // 돌
        g.setFill(Color.rgb(190, 190, 190));
        for (Rock r : rocks) {
            g.fillOval(r.x - r.r, r.y - r.r, r.r * 2, r.r * 2);
        }

        // 충돌 플래시
        if (flash > 0) {
            g.setFill(Color.rgb(255, 60, 60, 0.28 * flash));
            g.fillRect(0, 0, W, H);
        }
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }
}

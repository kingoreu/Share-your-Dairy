package com.share.dairy.util.game;

import javafx.animation.AnimationTimer;
import javafx.geometry.Insets;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;

import java.util.*;
import java.util.function.IntConsumer;

public final class TetrisPane extends StackPane {
    // ===== 보드/색 =====
    private static final int COLS = 10;
    private static final int ROWS = 22;         // 상단 2줄 히든
    private static final int VISIBLE_ROWS = 20;

    private static final Color[] COLORS = {
            Color.TRANSPARENT,
            Color.rgb(  0, 240, 240), // I
            Color.rgb(  0,   0, 240), // J
            Color.rgb(240, 160,   0), // L
            Color.rgb(240, 240,   0), // O
            Color.rgb(  0, 240,   0), // S
            Color.rgb(160,   0, 240), // T
            Color.rgb(240,   0,   0)  // Z
    };

    private static final boolean[][][][] SHAPES = TetrominoShapes.build();

    // ===== 렌더 =====
    private final Canvas canvas = new Canvas();
    private final GraphicsContext g;
    private final double W, H;
    private int cell;
    private double ox, oy;

    // ===== 상태 =====
    private final int[][] board = new int[ROWS][COLS];
    private Piece cur, next;
    private final Queue<Integer> bag = new ArrayDeque<>();
    private final Random rnd = new Random();

    private int score = 0, lines = 0, level = 1;

    // 진행률 시각화(0~1) — 속도엔 영향 X
    private double tint = 0;

    // 중력
    private double fallTimer = 0;
    private double fallInterval = 0.50;   // 기본 고정 속도
    private boolean useLevelSpeed = false;

    // 입력 유지(DAS/ARR)
    private boolean leftHeld=false, rightHeld=false, downHeld=false;
    private double das=0, arr=0;
    private static final double DAS_TIME=0.15, ARR_TIME=0.04;

    // 소프트 드롭 유지
    private double softDropTimer = 0;
    private static final double SOFT_STEP = 0.03;

    // 고스트
    private int ghostY;

    // 루프/상태
    private AnimationTimer loop;
    private long lastNs;
    private boolean paused = false;
    private boolean gameOver = false;
    private boolean stopped = false;

    // 시작/도움말
    private boolean started = false;
    private boolean showHelp = true;
    private double helpBlink = 0;

    // 타임아웃(0=무제한)
    private double timeLeft = 0;

    // 콜백
    private IntConsumer onFinished;

    // ===== 키 핸들러: Scene 캡처 단계(EventFilter)로 등록 =====
    private final javafx.event.EventHandler<KeyEvent> onKeyPressedFilter = e -> {
        if (stopped) return;

        KeyCode code = e.getCode();

        // 시작 전: F1로만 시작
        if (!started) {
            if (code == KeyCode.F1) {
                started = true;
                showHelp = false;
                e.consume();
            }
            return;
        }

        // 게임 중 F1: 도움말 토글 + 일시정지/해제
        if (code == KeyCode.F1) {
            if (!gameOver) {
                paused = !paused;
                showHelp = paused;
                e.consume();
            }
            return;
        }

        // 전역 토글
        if (code == KeyCode.P) { paused = !paused; e.consume(); return; }
        if (gameOver && code == KeyCode.R) { restart(); e.consume(); return; }
        if (gameOver || paused) return;

        switch (code){
            case LEFT, A -> { leftHeld = true; move(-1); das = 0; e.consume(); }
            case RIGHT, D -> { rightHeld = true; move(+1); das = 0; e.consume(); }
            case DOWN, S -> { downHeld = true; softDrop(); e.consume(); }
            case SPACE -> { hardDrop(); e.consume(); }
            case UP, X -> { rotate(+1); e.consume(); }
            case Z -> { rotate(-1); e.consume(); }
            default -> {}
        }
    };

    private final javafx.event.EventHandler<KeyEvent> onKeyReleasedFilter = e -> {
        switch (e.getCode()){
            case LEFT, A -> { leftHeld = false; das = 0; arr = 0; e.consume(); }
            case RIGHT, D -> { rightHeld = false; das = 0; arr = 0; e.consume(); }
            case DOWN, S -> { downHeld = false; e.consume(); }
            default -> {}
        }
    };

    public TetrisPane(double width, double height) {
        setPadding(new Insets(12));
        setMaxSize(USE_PREF_SIZE, USE_PREF_SIZE);
        setStyle("-fx-background-color: rgba(255,255,255,0.08); -fx-background-radius: 12;");

        this.W = width; this.H = height;
        canvas.setWidth(W); canvas.setHeight(H);
        getChildren().add(canvas);
        g = canvas.getGraphicsContext2D();

        // 셀/오프셋
        this.cell = (int)Math.floor(Math.min(W / COLS, H / VISIBLE_ROWS));
        this.ox = (W - cell * COLS) / 2.0;
        this.oy = (H - cell * VISIBLE_ROWS) / 2.0;

        // Scene에 필터 등록/해제
        sceneProperty().addListener((obs, oldSc, sc) -> {
            if (oldSc != null) {
                oldSc.removeEventFilter(KeyEvent.KEY_PRESSED, onKeyPressedFilter);
                oldSc.removeEventFilter(KeyEvent.KEY_RELEASED, onKeyReleasedFilter);
            }
            if (sc != null) {
                sc.addEventFilter(KeyEvent.KEY_PRESSED, onKeyPressedFilter);
                sc.addEventFilter(KeyEvent.KEY_RELEASED, onKeyReleasedFilter);
            }
        });

        // 포커스 잃으면 held 플래그 리셋
        focusedProperty().addListener((o, was, now) -> {
            if (!now) {
                leftHeld = rightHeld = downHeld = false;
                das = arr = 0;
                softDropTimer = 0;
            }
        });
        setOnMouseClicked(e -> requestFocus());
        setFocusTraversable(true);

        // 초기화
        fallInterval = useLevelSpeed ? baseIntervalForLevel(level) : fallInterval;
        next = new Piece(nextType(), 0, 3, -1);
        spawn();

        loop = new AnimationTimer() {
            @Override public void handle(long now) {
                if (lastNs==0){ lastNs=now; draw(); return; }
                double dt = (now-lastNs)/1_000_000_000.0;
                if (dt > 0.1) dt = 0.1; // 프리즈 클램프
                lastNs = now;
                update(dt);
                draw();
            }
        };
        loop.start();
    }

    // ===== 외부 API =====
    public void requestGameFocus(){ requestFocus(); }
    public void setProgressTint(int percent){
        percent = Math.max(0, Math.min(100, percent));
        tint = percent / 100.0;
        if (percent >= 100) closeAndReport();
    }
    public void stop(){
        stopped = true;
        if (getScene()!=null){
            getScene().removeEventFilter(KeyEvent.KEY_PRESSED, onKeyPressedFilter);
            getScene().removeEventFilter(KeyEvent.KEY_RELEASED, onKeyReleasedFilter);
        }
        if (loop!=null){ loop.stop(); loop=null; }
    }
    public void setOnFinished(IntConsumer cb){ this.onFinished = cb; }
    public void setSessionSeconds(int sec){ this.timeLeft = Math.max(0, sec); }
    public void setUseLevelSpeed(boolean v){
        useLevelSpeed = v;
        fallInterval = v ? baseIntervalForLevel(level) : fallInterval;
    }
    public void setFixedFallInterval(double seconds){
        fallInterval = Math.max(0.05, seconds);
        if (!useLevelSpeed) fallInterval = seconds;
    }

    // ===== 로직 =====
    private void update(double dt){
        if (stopped) return;
        if (!started) { helpBlink += dt; return; }
        if (paused) { helpBlink += dt; return; }
        if (timeLeft > 0){
            timeLeft = Math.max(0, timeLeft - dt);
            if (timeLeft == 0) closeAndReport();
        }
        if (gameOver) return;

        // DAS/ARR
        if (leftHeld ^ rightHeld){
            das += dt;
            if (das >= DAS_TIME){
                arr += dt;
                while (arr >= ARR_TIME){
                    arr -= ARR_TIME;
                    move(leftHeld ? -1 : +1);
                }
            }
        }

        // 소프트 드롭 유지
        if (downHeld){
            softDropTimer += dt;
            while (softDropTimer >= SOFT_STEP){
                softDropTimer -= SOFT_STEP;
                softDrop();
            }
        }

        // 중력
        fallTimer += dt;
        while (fallTimer >= fallInterval){
            fallTimer -= fallInterval;
            if (!tryMove(cur, 0, +1)){
                lockPiece();
                clearLines();
                spawn();
            }
        }

        ghostY = ghostDropY();
    }

    private void move(int dx){ tryMove(cur, dx, 0); }
    private void softDrop(){ if (tryMove(cur, 0, +1)) score += 1; }
    private void hardDrop(){
        int dy = 0;
        while (tryMove(cur, 0, +1)) dy++;
        score += dy * 2;
        lockPiece(); clearLines(); spawn();
    }
    private void rotate(int dir){
        Piece nextRot = cur.rotate(dir);
        int[] kicks = {0, +1, -1, +2, -2};
        for (int k : kicks){
            if (canPlace(nextRot.x + k, nextRot.y, nextRot.type, nextRot.rot)){
                cur = new Piece(nextRot.type, nextRot.rot, nextRot.x + k, nextRot.y);
                ghostY = ghostDropY();
                return;
            }
        }
    }
    private void spawn(){
        cur = new Piece(next.type, 0, 3, -1);
        if (!canPlace(cur.x, cur.y, cur.type, cur.rot)){
            gameOver = true; return;
        }
        next = new Piece(nextType(), 0, 3, -1);
        fallTimer = 0; das = arr = 0;
        ghostY = ghostDropY();
    }
    private void lockPiece(){
        forEachCell(cur.type, cur.rot, (cx, cy) -> {
            int bx = cur.x + cx, by = cur.y + cy;
            if (by>=0 && by<ROWS && bx>=0 && bx<COLS) board[by][bx] = cur.type;
        });
    }
    private void clearLines(){
        int cleared = 0;
        for (int r=ROWS-1; r>=0; r--){
            boolean full = true;
            for (int c=0;c<COLS;c++) if (board[r][c]==0){ full=false; break; }
            if (full){
                cleared++;
                for (int rr=r; rr>0; rr--) System.arraycopy(board[rr-1], 0, board[rr], 0, COLS);
                Arrays.fill(board[0], 0);
                r++;
            }
        }
        if (cleared>0){
            lines += cleared;
            score += switch (cleared){
                case 1 -> 100 * level;
                case 2 -> 300 * level;
                case 3 -> 500 * level;
                case 4 -> 800 * level;
                default -> 0;
            };
            int newLevel = 1 + lines/10;
            if (newLevel != level){
                level = newLevel;
                if (useLevelSpeed) fallInterval = baseIntervalForLevel(level);
            }
        }
    }
    private int ghostDropY(){
        Piece p = new Piece(cur.type, cur.rot, cur.x, cur.y);
        while (canPlace(p.x, p.y+1, p.type, p.rot)) p.y++;
        return p.y;
    }
    private boolean tryMove(Piece p, int dx, int dy){
        if (canPlace(p.x+dx, p.y+dy, p.type, p.rot)){ p.x+=dx; p.y+=dy; return true; }
        return false;
    }
    private boolean canPlace(int x, int y, int type, int rot){
        final boolean[][] m = SHAPES[type-1][rot];
        for (int r=0;r<4;r++) for (int c=0;c<4;c++){
            if (!m[r][c]) continue;
            int bx = x + c, by = y + r;
            if (bx<0 || bx>=COLS || by>=ROWS) return false;
            if (by>=0 && board[by][bx]!=0) return false;
        }
        return true;
    }
    private int nextType(){
        if (bag.isEmpty()){
            List<Integer> seven = Arrays.asList(1,2,3,4,5,6,7);
            Collections.shuffle(seven, rnd);
            bag.addAll(seven);
        }
        return bag.remove();
    }
    private double baseIntervalForLevel(int lv){
        return switch (Math.min(lv, 20)){
            case 1 -> 0.60; case 2 -> 0.55; case 3 -> 0.50; case 4 -> 0.45; case 5 -> 0.40;
            case 6 -> 0.35; case 7 -> 0.30; case 8 -> 0.20; case 9 -> 0.14; default -> 0.12;
        };
    }

    private void closeAndReport(){
        if (stopped) return;
        new AnimationTimer(){
            long t0;
            @Override public void handle(long now){
                if (t0==0) t0=now;
                if ((now - t0)/1e9 >= 0.5){
                    TetrisPane.this.stop();
                    if (onFinished!=null) onFinished.accept(score);
                    this.stop();
                }
            }
        }.start();
    }

    // ===== 그리기 =====
    private void draw(){
        g.setFill(Color.rgb(12,14,22)); g.fillRect(0,0,W,H);

        if (tint>0){
            g.setFill(Color.rgb(120,200,255, 0.08 + 0.12*tint));
            g.fillRect(ox-16, oy-16, cell*COLS+32, cell*VISIBLE_ROWS+32);
        }

        g.setFill(Color.rgb(24,26,34));
        g.fillRect(ox, oy, cell*COLS, cell*VISIBLE_ROWS);

        for (int r=2;r<ROWS;r++){
            for (int c=0;c<COLS;c++){
                int id = board[r][c];
                if (id!=0){
                    drawCell(c, r-2, id, 1.0);
                } else {
                    g.setStroke(Color.rgb(60,64,78,0.35));
                    g.strokeRect(ox+c*cell, oy+(r-2)*cell, cell, cell);
                }
            }
        }

        if (!gameOver){
            forEachCell(cur.type, cur.rot, (cx, cy) -> {
                int gx = cur.x + cx, gy = ghostY + cy;
                if (gy>=2){
                    g.setStroke(Color.rgb(255,255,255,0.25));
                    g.strokeRect(ox+gx*cell, oy+(gy-2)*cell, cell, cell);
                }
            });
        }

        if (!gameOver){
            forEachCell(cur.type, cur.rot, (cx, cy) -> {
                int bx = cur.x + cx, by = cur.y + cy;
                if (by>=2) drawCell(bx, by-2, cur.type, 1.0);
            });
        }

        g.setFill(Color.rgb(235,240,255,0.95));
        g.fillText("Score: "+score, ox + cell*COLS + 16, oy + 20);
        g.fillText("Level: "+level, ox + cell*COLS + 16, oy + 40);
        g.fillText("Lines: "+lines, ox + cell*COLS + 16, oy + 60);
        if (timeLeft>0) g.fillText(String.format("Time: %.0f", Math.ceil(timeLeft)), ox + cell*COLS + 16, oy + 80);

        g.fillText("Next", ox - 66, oy + 20);
        drawMini(next.type, ox - 86, oy + 30);

        if ((!started || (paused && showHelp)) && !gameOver) drawHelpOverlay();

        if (gameOver){
            g.setFill(Color.rgb(0,0,0,0.55));
            g.fillRect(0,0,W,H);
            g.setFill(Color.rgb(255,255,255,0.95));
            g.fillText("GAME OVER", W/2 - 40, H/2 - 6);
            g.fillText("Score: "+score, W/2 - 28, H/2 + 14);
            g.fillText("Press R to Retry", W/2 - 50, H/2 + 34);
        }
    }

    private void drawHelpOverlay(){
        double panelW = Math.min(W*0.72, 360);
        double panelH = Math.min(H*0.72, 230);
        double x = (W - panelW)/2, y = (H - panelH)/2;

        g.setFill(Color.rgb(0,0,0,0.66));
        g.fillRoundRect(x, y, panelW, panelH, 12, 12);
        g.setStroke(Color.rgb(255,255,255,0.18));
        g.strokeRoundRect(x+0.5, y+0.5, panelW-1, panelH-1, 12, 12);

        g.setFill(Color.rgb(220,235,255,0.98));
        g.fillText("조작 안내", x + 16, y + 24);

        double line = y + 48, lh = 18;
        g.setFill(Color.rgb(235,240,255,0.95));
        g.fillText("←/→  또는 A/D : 좌우 이동", x + 16, line); line += lh;
        g.fillText("↓     또는 S   : 소프트 드롭(+1점/칸)", x + 16, line); line += lh;
        g.fillText("SPACE            : 하드 드롭(+2점/칸)", x + 16, line); line += lh;
        g.fillText("↑/X              : 시계 방향 회전", x + 16, line); line += lh;
        g.fillText("Z                 : 반시계 방향 회전", x + 16, line); line += lh;
        g.fillText("P                 : 일시정지/해제", x + 16, line); line += lh;
        g.fillText("R (게임오버 시)  : 재시작", x + 16, line); line += lh;
        g.fillText("F1                : 시작/일시정지(도움말)", x + 16, line); line += lh;

        double a = 0.35 + 0.65 * Math.abs(Math.sin(helpBlink * 2.6));
        g.setFill(Color.rgb(255,255,255, a));
        g.fillText(!started ? "F1을 눌러 시작" : "F1을 눌러 계속", x + 16, y + panelH - 16);
    }

    private void drawCell(int x, int y, int id, double alpha){
        Color base = COLORS[id];
        g.setFill(Color.color(base.getRed(), base.getGreen(), base.getBlue(), 0.95*alpha));
        double px = ox + x*cell, py = oy + y*cell;
        g.fillRoundRect(px+1, py+1, cell-2, cell-2, 6, 6);
        g.setStroke(Color.color(1,1,1,0.20*alpha));
        g.strokeLine(px+2, py+2, px+cell-4, py+2);
        g.setStroke(Color.color(0,0,0,0.25*alpha));
        g.strokeLine(px+2, py+cell-3, px+cell-3, py+cell-3);
    }
    private void drawMini(int type, double mx, double my){
        final double mini = cell * 0.6;
        for (int r=0;r<4;r++) for (int c=0;c<4;c++){
            if (SHAPES[type-1][0][r][c]){
                Color base = COLORS[type];
                g.setFill(Color.color(base.getRed(), base.getGreen(), base.getBlue(), 0.9));
                g.fillRoundRect(mx + c*mini, my + r*mini, mini-2, mini-2, 4, 4);
            }
        }
    }

    private interface CellConsumer { void accept(int cx, int cy); }
    private void forEachCell(int type, int rot, CellConsumer cc){
        final boolean[][] m = SHAPES[type-1][rot];
        for (int r=0;r<4;r++) for (int c=0;c<4;c++) if (m[r][c]) cc.accept(c, r);
    }

    private static final class Piece {
        final int type;  // 1..7
        final int rot;   // 0..3
        int x, y;
        Piece(int t, int r, int x, int y){ this.type=t; this.rot=r; this.x=x; this.y=y; }
        Piece rotate(int dir){ int nr = (rot + (dir>0?1:3)) & 3; return new Piece(type, nr, x, y); }
    }

    public void restart(){
        for (int r=0; r<ROWS; r++) Arrays.fill(board[r], 0);
        score = 0; lines = 0; level = 1;
        fallInterval = useLevelSpeed ? baseIntervalForLevel(level) : fallInterval;
        bag.clear();
        next = new Piece(nextType(), 0, 3, -1);
        leftHeld = rightHeld = downHeld = false;
        das = arr = 0; fallTimer = 0; softDropTimer = 0;
        paused = false; gameOver = false;
        cur = null;
        started = true; showHelp = false; helpBlink = 0;
        spawn();
        requestFocus();
    }

    private static final class TetrominoShapes {
        static boolean[][][][] build(){
            boolean[][][][] s = new boolean[7][4][4][4];
            s[0][0] = mask("....","####","....","....");
            s[0][1] = mask("..#.","..#.","..#.","..#."); s[0][2] = s[0][0]; s[0][3] = s[0][1];
            s[1][0] = mask("#..","###","...","...");    s[1][1] = mask(".##",".#.",".#.","...");
            s[1][2] = mask("...","###","..#","...");    s[1][3] = mask(".#.",".#.","##.","...");
            s[2][0] = mask("..#","###","...","...");    s[2][1] = mask(".#.",".#.",".##","...");
            s[2][2] = mask("...","###","#..","...");    s[2][3] = mask("##.",".#.",".#.","...");
            s[3][0] = mask(".##.",".##.","....","...."); s[3][1]=s[3][2]=s[3][3]=s[3][0];
            s[4][0] = mask(".##","##.","...","...");    s[4][1] = mask(".#.",".##","..#","..."); s[4][2]=s[4][0]; s[4][3]=s[4][1];
            s[5][0] = mask(".#.","###","...","...");    s[5][1] = mask(".#.",".##",".#.","..."); s[5][2] = mask("...","###",".#.","..."); s[5][3]=mask(".#.","##.",".#.","...");
            s[6][0] = mask("##.",".##","...","...");    s[6][1] = mask("..#", ".##", ".#.","..."); s[6][2]=s[6][0]; s[6][3]=s[6][1];
            return s;
        }
        private static boolean[][] mask(String... rows){
            boolean[][] m = new boolean[4][4];
            for (int r=0;r<4;r++){
                String row = (r<rows.length? rows[r] : "....");
                for (int c=0;c<4;c++){
                    char ch = (c<row.length()? row.charAt(c) : '.');
                    m[r][c] = (ch=='#');
                }
            }
            return m;
        }
    }
}

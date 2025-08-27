package com.share.dairy.util.game;

import javafx.animation.AnimationTimer;
import javafx.geometry.Insets;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;

import java.util.*;
import java.util.function.IntConsumer;

/**
 * TetrisPane - JavaFX Canvas Tetris
 * - 진행률(tint)은 배경만 바꾸고 낙하속도에는 영향 X
 * - F1: 시작/일시정지(도움말 토글)
 * - P: 일시정지/해제(도움말은 그대로)
 * - R: 게임오버에서 재시작
 *
 * 외부 API
 *  - requestGameFocus()
 *  - setProgressTint(int percent)   // 0~100, 100%면 닫기
 *  - stop()
 *  - setOnFinished(IntConsumer cb)  // 닫힐 때 점수 전달
 *  - setSessionSeconds(int sec)     // 0이면 무제한
 *
 * 조작
 *  ←/→(A/D): 좌우 이동  |  ↓(S): 소프트 드롭
 *  ↑/X: 시계 회전      |  Z: 반시계 회전
 *  SPACE: 하드 드롭    |  F1: 시작/일시정지(도움말)
 *  P: 일시정지/해제    |  R: (게임오버) 재시작
 */
public final class TetrisPane extends StackPane {

    // 보드
    private static final int COLS = 10;
    private static final int ROWS = 22;         // 상단 2줄 히든
    private static final int VISIBLE_ROWS = 20;

    // 색상
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

    // 모양(7 × 4회전 × 4×4)
    private static final boolean[][][][] SHAPES = TetrominoShapes.build();

    // 렌더
    private final Canvas canvas = new Canvas();
    private final GraphicsContext g;
    private final double W, H;
    private int cell;
    private double ox, oy;

    // 상태
    private final int[][] board = new int[ROWS][COLS];
    private Piece cur, next;
    private final Queue<Integer> bag = new ArrayDeque<>();
    private final Random rnd = new Random();

    // 점수/레벨
    private int score = 0, lines = 0, level = 1;

    // 진행률 시각화(0~1). 속도엔 영향 X
    private double tint = 0;

    // 중력
    private double fallTimer = 0;
    private double fallInterval = 0.8;    // baseIntervalForLevel(level)로 갱신

    // 입력 유지 (DAS/ARR)
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
    private boolean started = false;   // 시작 여부
    private boolean showHelp = true;   // 도움말 표시 여부
    private double helpBlink = 0;      // 하단 문구 점멸

    // 타임아웃(0=무제한)
    private double timeLeft = 0;

    // 닫힐 때 점수 콜백
    private IntConsumer onFinished;

    public TetrisPane(double width, double height) {
        setPadding(new Insets(12));
        setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        setStyle("-fx-background-color: rgba(255,255,255,0.08); -fx-background-radius: 12;");

        this.W = width; this.H = height;
        canvas.setWidth(W); canvas.setHeight(H);
        getChildren().add(canvas);
        g = canvas.getGraphicsContext2D();

        // 셀 크기/오프셋
        this.cell = (int)Math.floor(Math.min(W / COLS, H / VISIBLE_ROWS));
        this.ox = (W - cell * COLS) / 2.0;
        this.oy = (H - cell * VISIBLE_ROWS) / 2.0;

        // 입력
        setFocusTraversable(true);
        setOnKeyPressed(e -> {
            if (stopped) return;

            // 시작 전: F1로만 시작
            if (!started) {
                if (e.getCode() == KeyCode.F1) {
                    started = true;
                    showHelp = false;   // 도움말 닫고 시작
                }
                return; // 그 외 키는 무시
            }

            // 게임 중 F1: 도움말 토글 + 일시정지/해제
            if (e.getCode() == KeyCode.F1) {
                if (!gameOver) {
                    paused = !paused;
                    showHelp = paused;  // 일시정지 시 도움말 표시, 해제 시 숨김
                }
                return;
            }

            if (e.getCode()==KeyCode.P) { paused = !paused; return; }
            if (gameOver && e.getCode()==KeyCode.R) { restart(); return; }
            if (gameOver || paused) return;

            switch (e.getCode()){
                case LEFT, A -> { leftHeld = true; move(-1); das = 0; }
                case RIGHT, D -> { rightHeld = true; move(+1); das = 0; }
                case DOWN, S -> { downHeld = true; softDrop(); }
                case SPACE -> hardDrop();
                case UP, X -> rotate(+1);
                case Z -> rotate(-1);
            }
        });
        setOnKeyReleased(e -> {
            switch (e.getCode()){
                case LEFT, A -> { leftHeld = false; das = 0; arr = 0; }
                case RIGHT, D -> { rightHeld = false; das = 0; arr = 0; }
                case DOWN, S -> { downHeld = false; }
            }
        });

        // 초기화
        fallInterval = baseIntervalForLevel(level);
        next = new Piece(nextType(), 0, 3, -1);
        spawn();

        // 루프 시작
        loop = new AnimationTimer() {
            @Override public void handle(long now) {
                if (lastNs==0){ lastNs=now; draw(); return; }
                double dt = (now-lastNs)/1_000_000_000.0;
                lastNs = now;
                update(dt);
                draw();
            }
        };
        loop.start();
    }

    // ===== 외부 API =====
    public void requestGameFocus(){ requestFocus(); }

    // 진행률: 화면만 변화, 100%면 닫기
    public void setProgressTint(int percent){
        percent = Math.max(0, Math.min(100, percent));
        tint = percent / 100.0;
        if (percent >= 100) closeAndReport();
    }

    public void stop(){
        stopped = true;
        if (loop!=null){ loop.stop(); loop=null; }
    }
    public void setOnFinished(IntConsumer cb){ this.onFinished = cb; }
    public void setSessionSeconds(int sec){ this.timeLeft = Math.max(0, sec); }

    // ===== 로직 =====
    private void update(double dt){
        if (stopped) return;

        // 시작 전: 안내 점멸만 갱신
        if (!started) {
            helpBlink += dt;
            return;
        }

        // 일시정지: 진행 로직 정지(도움말은 draw()에서 그림)
        if (paused) {
            helpBlink += dt; // 일시정지 중에도 하단 텍스트 점멸
            return;
        }

        // 시간 제한(시작 후 & 일시정지 아님)
        if (timeLeft > 0){
            timeLeft = Math.max(0, timeLeft - dt);
            if (timeLeft == 0) closeAndReport();
        }

        // 게임오버 상태: 재시작 대기만
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
                // 게임오버여도 닫지 않음 (R로 재시작 가능)
            }
        }

        // 고스트
        ghostY = ghostDropY();
    }

    private void move(int dx){ tryMove(cur, dx, 0); }

    private void softDrop(){
        if (tryMove(cur, 0, +1)) score += 1;
    }

    private void hardDrop(){
        int dy = 0;
        while (tryMove(cur, 0, +1)) dy++;
        score += dy * 2;
        lockPiece();
        clearLines();
        spawn();
        // 게임오버여도 닫지 않음
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
            gameOver = true;
            return;
        }
        next = new Piece(nextType(), 0, 3, -1);
        fallTimer = 0;
        das = arr = 0;
        ghostY = ghostDropY();
    }

    private void lockPiece(){
        forEachCell(cur.type, cur.rot, (cx, cy) -> {
            int bx = cur.x + cx;
            int by = cur.y + cy;
            if (by>=0 && by<ROWS && bx>=0 && bx<COLS){
                board[by][bx] = cur.type;
            }
        });
    }

    private void clearLines(){
        int cleared = 0;
        for (int r=ROWS-1; r>=0; r--){
            boolean full = true;
            for (int c=0;c<COLS;c++){
                if (board[r][c]==0){ full=false; break; }
            }
            if (full){
                cleared++;
                for (int rr=r; rr>0; rr--){
                    System.arraycopy(board[rr-1], 0, board[rr], 0, COLS);
                }
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
                fallInterval = baseIntervalForLevel(level); // 진행률과 무관
            }
        }
    }

    private int ghostDropY(){
        Piece p = new Piece(cur.type, cur.rot, cur.x, cur.y);
        while (canPlace(p.x, p.y+1, p.type, p.rot)) p.y++;
        return p.y;
    }

    private boolean tryMove(Piece p, int dx, int dy){
        if (canPlace(p.x+dx, p.y+dy, p.type, p.rot)){
            p.x += dx; p.y += dy;
            return true;
        }
        return false;
    }

    private boolean canPlace(int x, int y, int type, int rot){
        final boolean[][] m = SHAPES[type-1][rot];
        for (int r=0;r<4;r++){
            for (int c=0;c<4;c++){
                if (!m[r][c]) continue;
                int bx = x + c;
                int by = y + r;
                if (bx<0 || bx>=COLS || by>=ROWS) return false;
                if (by>=0 && board[by][bx]!=0) return false;
            }
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

    /** 진행 100%/시간 만료 시 오버레이 닫고 점수 보고 */
    private void closeAndReport(){
        if (stopped) return;
        new AnimationTimer(){
            long t0;
            @Override public void handle(long now){
                if (t0==0) t0=now;
                if ((now - t0)/1e9 >= 0.5){
                    stop();
                    if (onFinished!=null) onFinished.accept(score);
                    this.stop();
                }
            }
        }.start();
    }

    // ===== 그리기 =====
    private void draw(){
        // 배경
        g.setFill(Color.rgb(12,14,22));
        g.fillRect(0,0,W,H);

        // 진행률 틴트(시각화 전용)
        if (tint>0){
            g.setFill(Color.rgb(120,200,255, 0.08 + 0.12*tint));
            g.fillRect(ox-16, oy-16, cell*COLS+32, cell*VISIBLE_ROWS+32);
        }

        // 보드 배경
        g.setFill(Color.rgb(24,26,34));
        g.fillRect(ox, oy, cell*COLS, cell*VISIBLE_ROWS);

        // 타일/그리드
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

        // 고스트
        if (!gameOver){
            forEachCell(cur.type, cur.rot, (cx, cy) -> {
                int gx = cur.x + cx;
                int gy = ghostY + cy;
                if (gy>=2){
                    g.setStroke(Color.rgb(255,255,255,0.25));
                    g.strokeRect(ox+gx*cell, oy+(gy-2)*cell, cell, cell);
                }
            });
        }

        // 현재 피스
        if (!gameOver){
            forEachCell(cur.type, cur.rot, (cx, cy) -> {
                int bx = cur.x + cx;
                int by = cur.y + cy;
                if (by>=2) drawCell(bx, by-2, cur.type, 1.0);
            });
        }

        // UI
        g.setFill(Color.rgb(235,240,255,0.95));
        g.fillText("Score: "+score, ox + cell*COLS + 16, oy + 20);
        g.fillText("Level: "+level, ox + cell*COLS + 16, oy + 40);
        g.fillText("Lines: "+lines, ox + cell*COLS + 16, oy + 60);
        if (timeLeft>0) g.fillText(String.format("Time: %.0f", Math.ceil(timeLeft)), ox + cell*COLS + 16, oy + 80);

        // Next
        g.fillText("Next", ox - 66, oy + 20);
        drawMini(next.type, ox - 86, oy + 30);

        // 도움말(시작 전 또는 F1로 일시정지한 상태) — 게임오버 때는 표시 안 함
        if ((!started || (paused && showHelp)) && !gameOver) {
            drawHelpOverlay();
        }

        // GAME OVER 오버레이 (R로 재시작 안내)
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
        double x = (W - panelW)/2;
        double y = (H - panelH)/2;

        // 배경 패널
        g.setFill(Color.rgb(0,0,0,0.66));
        g.fillRoundRect(x, y, panelW, panelH, 12, 12);
        g.setStroke(Color.rgb(255,255,255,0.18));
        g.strokeRoundRect(x+0.5, y+0.5, panelW-1, panelH-1, 12, 12);

        // 타이틀
        g.setFill(Color.rgb(220,235,255,0.98));
        g.fillText("조작 안내", x + 16, y + 24);

        // 내용
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

        // 하단 문구: 시작 전/일시정지에서 서로 다르게
        double a = 0.35 + 0.65 * Math.abs(Math.sin(helpBlink * 2.6));
        g.setFill(Color.rgb(255,255,255, a));
        String footer = !started ? "F1을 눌러 시작" : "F1을 눌러 계속";
        g.fillText(footer, x + 16, y + panelH - 16);
    }

    private void drawCell(int x, int y, int id, double alpha){
        Color base = COLORS[id];
        g.setFill(Color.color(base.getRed(), base.getGreen(), base.getBlue(), 0.95*alpha));
        double px = ox + x*cell;
        double py = oy + y*cell;
        g.fillRoundRect(px+1, py+1, cell-2, cell-2, 6, 6);
        g.setStroke(Color.color(1,1,1,0.20*alpha));
        g.strokeLine(px+2, py+2, px+cell-4, py+2);
        g.setStroke(Color.color(0,0,0,0.25*alpha));
        g.strokeLine(px+2, py+cell-3, px+cell-3, py+cell-3);
    }

    private void drawMini(int type, double mx, double my){
        final double mini = cell * 0.6;
        for (int r=0;r<4;r++){
            for (int c=0;c<4;c++){
                if (SHAPES[type-1][0][r][c]){
                    Color base = COLORS[type];
                    g.setFill(Color.color(base.getRed(), base.getGreen(), base.getBlue(), 0.9));
                    g.fillRoundRect(mx + c*mini, my + r*mini, mini-2, mini-2, 4, 4);
                }
            }
        }
    }

    // 유틸
    private interface CellConsumer { void accept(int cx, int cy); }
    private void forEachCell(int type, int rot, CellConsumer cc){
        final boolean[][] m = SHAPES[type-1][rot];
        for (int r=0;r<4;r++)
            for (int c=0;c<4;c++)
                if (m[r][c]) cc.accept(c, r);
    }

    // 데이터
    private static final class Piece {
        final int type;  // 1..7
        final int rot;   // 0..3
        int x, y;        // 4x4 마스크 좌상단
        Piece(int t, int r, int x, int y){ this.type=t; this.rot=r; this.x=x; this.y=y; }
        Piece rotate(int dir){ int nr = (rot + (dir>0?1:3)) & 3; return new Piece(type, nr, x, y); }
    }

    // 재시작
    public void restart(){
        for (int r=0; r<ROWS; r++) Arrays.fill(board[r], 0);
        score = 0; lines = 0; level = 1;
        fallInterval = baseIntervalForLevel(level);
        bag.clear();
        next = new Piece(nextType(), 0, 3, -1);
        paused = false; gameOver = false;
        cur = null;
        started = false;         // 다시 시작 전 도움말 표시
        showHelp = true;
        helpBlink = 0;
        spawn();
    }

    private static final class TetrominoShapes {
        static boolean[][][][] build(){
            boolean[][][][] s = new boolean[7][4][4][4];

            // I
            s[0][0] = mask("....","####","....","....");
            s[0][1] = mask("..#.","..#.","..#.","..#.");
            s[0][2] = s[0][0];
            s[0][3] = s[0][1];

            // J
            s[1][0] = mask("#..","###","...","...");
            s[1][1] = mask(".##",".#.",".#.","...");
            s[1][2] = mask("...","###","..#","...");
            s[1][3] = mask(".#.",".#.","##.","...");

            // L
            s[2][0] = mask("..#","###","...","...");
            s[2][1] = mask(".#.",".#.",".##","...");
            s[2][2] = mask("...","###","#..","...");
            s[2][3] = mask("##.",".#.",".#.","...");

            // O
            s[3][0] = mask(".##.",".##.","....","....");
            s[3][1] = s[3][0]; s[3][2] = s[3][0]; s[3][3] = s[3][0];

            // S
            s[4][0] = mask(".##","##.","...","...");
            s[4][1] = mask(".#.",".##","..#","...");
            s[4][2] = s[4][0];
            s[4][3] = s[4][1];

            // T
            s[5][0] = mask(".#.","###","...","...");
            s[5][1] = mask(".#.",".##",".#.","...");
            s[5][2] = mask("...","###",".#.","...");
            s[5][3] = mask(".#.","##.",".#.","...");

            // Z
            s[6][0] = mask("##.",".##","...","...");
            s[6][1] = mask("..#", ".##", ".#.","...");
            s[6][2] = s[6][0];
            s[6][3] = s[6][1];

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

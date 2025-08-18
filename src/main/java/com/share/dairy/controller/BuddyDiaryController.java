package com.share.dairy.controller;

import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;   // ✅
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.*;
import javafx.scene.shape.Rectangle;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class BuddyDiaryController {

    @FXML private GridPane entriesGrid; // 우측 2×2 그리드
    @FXML private VBox buddyList;       // 좌측 버디 리스트

    private String selectedBuddyId;
    private boolean gridInitialized = false;

    private final Label[] dateLabels = new Label[4];
    private final Label[] textLabels  = new Label[4];

    private static final boolean FAKE_DATA = true;
    private static final DateTimeFormatter DAY_FMT = DateTimeFormatter.ofPattern("yyyy.MM.dd");

    @FXML
    public void initialize() {
        // ESC 무력화 + 최초 버튼 크기 고정
        entriesGrid.sceneProperty().addListener((obs, o, s) -> {
            if (s != null) {
                s.addEventFilter(KeyEvent.KEY_PRESSED, e -> { if (e.getCode() == KeyCode.ESCAPE) e.consume(); });
                freezeAllButtonSizesOnce(s);
            }
        });

        // 우측 그리드 기본 여백(조금만)
        entriesGrid.setHgap(20);
        entriesGrid.setVgap(20);
        entriesGrid.setPadding(new Insets(12));
        setupGridConstraints();
        setupRowConstraints();

        // 그리드를 부모 크기에 맞춤
        if (entriesGrid.getParent() instanceof Region prGrid) {
            entriesGrid.prefWidthProperty().bind(prGrid.widthProperty());
            entriesGrid.prefHeightProperty().bind(prGrid.heightProperty());
        } else {
            entriesGrid.parentProperty().addListener((o, oldP, p) -> {
                if (p instanceof Region prGrid2) {
                    entriesGrid.prefWidthProperty().bind(prGrid2.widthProperty());
                    entriesGrid.prefHeightProperty().bind(prGrid2.heightProperty());
                }
            });
        }

        // ▶ 회색 패널 패딩을 최소화해서 내부 공간을 넓힘
        javafx.application.Platform.runLater(() -> {
            Region rightPanel = (Region) entriesGrid.getParent();
            if (rightPanel.getParent() instanceof Region pr) rightPanel.prefHeightProperty().bind(pr.heightProperty());
            if (rightPanel.getParent() instanceof HBox row) row.setFillHeight(true);
            rightPanel.setPadding(new Insets(8, 8, 8, 8)); // ← 8px
        });

        // 좌측 리스트 기본 세팅
        buddyList.setAlignment(Pos.TOP_CENTER);
        buddyList.setFillWidth(true);
        buddyList.setSpacing(12);
        buddyList.setPadding(new Insets(0, 10, 0, 10));
        if (buddyList.getParent() instanceof Region pr) pr.setPadding(Insets.EMPTY);

        // 데이터 렌더링
        List<Buddy> buddies = FAKE_DATA ? fakeBuddies() : fetchBuddiesFromDB();
        renderBuddyList(buddies);

        // 2×2 셀 생성
        ensureGridBuilt();

        // 스크롤 뷰포트/그리드 크기에 맞춰 자동 확장
        setupBuddyGridAutoResize();

        // 첫 친구 선택
        if (!buddies.isEmpty()) selectBuddy(buddies.get(0).id());
    }

    private void freezeAllButtonSizesOnce(Scene scene) {
        javafx.application.Platform.runLater(() -> {
            Parent root = scene.getRoot();
            root.applyCss();
            root.layout();
            for (Node n : root.lookupAll(".button")) {
                if (n instanceof Button b) {
                    double w = b.prefWidth(-1), h = b.prefHeight(-1);
                    b.setMinSize(w, h); b.setPrefSize(w, h); b.setMaxSize(w, h);
                }
            }
        });
    }

    @FXML private void loadWeekData() { loadWeekData(null); }
    @FXML private void loadWeekData(javafx.event.ActionEvent e) {
        if (selectedBuddyId == null || selectedBuddyId.isBlank()) return;
        var entries = FAKE_DATA ? fakeEntriesFor(selectedBuddyId) : fetchEntriesFromDB(selectedBuddyId);
        renderEntriesGrid(entries);
    }

    private void renderBuddyList(List<Buddy> buddies) {
        buddyList.getChildren().clear();
        for (Buddy b : buddies) buddyList.getChildren().add(buildBuddyItem(b));
    }

    private Node buildBuddyItem(Buddy b) {
        HBox card = new HBox(12);
        card.setAlignment(Pos.CENTER_LEFT);
        card.setPadding(new Insets(16));
        card.setMinHeight(64); card.setPrefHeight(68); card.setMaxHeight(80);
        card.setMinWidth(0);   card.setPrefWidth(Region.USE_COMPUTED_SIZE); card.setMaxWidth(Double.MAX_VALUE);

        final String BASE   = "-fx-background-color:#CBAFD1; -fx-background-radius:14;";
        final String HILITE = "-fx-background-color:white; -fx-background-radius:14; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.18), 8, 0, 0, 3);";
        card.setStyle(BASE);

        Label nameLabel = new Label(b.name());
        nameLabel.setStyle("-fx-font-size:17; -fx-font-weight:bold; -fx-text-fill:#141414;");
        card.getChildren().add(nameLabel);

        double GUTTER = buddyList.getPadding().getLeft();
        StackPane slot = new StackPane(card);
        slot.setAlignment(Pos.CENTER);
        slot.setMaxWidth(Double.MAX_VALUE);
        VBox.setMargin(slot, new Insets(8, 0, 8, 0));
        StackPane.setMargin(card, new Insets(0, GUTTER, 0, GUTTER));
        card.maxWidthProperty().bind(slot.widthProperty().subtract(GUTTER * 2));

        slot.setUserData(b.id());
        slot.setOnMouseClicked(e -> selectBuddy(b.id()));
        slot.setOnMouseEntered(e -> card.setStyle(HILITE));
        slot.setOnMouseExited(e -> card.setStyle(Objects.equals(slot.getUserData(), selectedBuddyId) ? HILITE : BASE));
        return slot;
    }

    private Node loadAvatar(String id) {
        try {
            Image img = new Image(Objects.requireNonNullElse(
                getClass().getResourceAsStream("/images/buddy/" + id + ".png"),
                getClass().getResourceAsStream("/images/buddy/_fallback.png")
            ));
            ImageView iv = new ImageView(img);
            iv.setFitWidth(36); iv.setFitHeight(36);
            Rectangle clip = new Rectangle(36, 36); clip.setArcWidth(36); clip.setArcHeight(36);
            iv.setClip(clip);
            return iv;
        } catch (Exception ignore) { return new Label(""); }
    }

    private void selectBuddy(String buddyId) {
        this.selectedBuddyId = buddyId;
        final String BASE = "-fx-background-color:#CBAFD1; -fx-background-radius:14;";
        final String HILITE = "-fx-background-color:white; -fx-background-radius:14; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.18), 8, 0.0, 0, 3);";
        for (Node slot : buddyList.getChildren()) {
            Node card = slot instanceof Pane p && !p.getChildren().isEmpty() ? p.getChildren().get(0) : slot;
            card.setStyle(Objects.equals(slot.getUserData(), buddyId) ? HILITE : BASE);
        }
        var entries = FAKE_DATA ? fakeEntriesFor(buddyId) : fetchEntriesFromDB(buddyId);
        renderEntriesGrid(entries);
    }

    // 2×2 셀 구성
    private void ensureGridBuilt() {
        if (gridInitialized) return;
        entriesGrid.getChildren().clear();
        for (int i = 0; i < 4; i++) {
            VBox cell = createCell(i);
            GridPane.setHgrow(cell, Priority.ALWAYS);
            GridPane.setVgrow(cell, Priority.ALWAYS);
            entriesGrid.add(cell, i % 2, i / 2);
        }
        gridInitialized = true;
    }

    private VBox createCell(int idx) {
        final double GAP_BELOW_DATE = 4; // 날짜와 카드 간격 ↓

        VBox wrap = new VBox(GAP_BELOW_DATE);
        wrap.setFillWidth(true);
        wrap.setMaxWidth(Double.MAX_VALUE);
        VBox.setVgrow(wrap, Priority.ALWAYS);

        Label date = new Label();
        date.setStyle("-fx-font-size:15; -fx-font-weight:bold; -fx-text-fill:#222;");
        dateLabels[idx] = date;

        StackPane card = new StackPane();
        card.setStyle("-fx-background-color:white; -fx-background-radius:16; -fx-effect:dropshadow(gaussian, rgba(0,0,0,0.14), 14, 0, 0, 4);");
        card.setPadding(new Insets(14));
        card.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        VBox.setVgrow(card, Priority.ALWAYS);
        // ▶ 셀 폭 100% 사용
        card.prefWidthProperty().bind(wrap.widthProperty());

        Rectangle clip = new Rectangle();
        clip.setArcWidth(16); clip.setArcHeight(16);
        clip.widthProperty().bind(card.widthProperty());
        clip.heightProperty().bind(card.heightProperty());
        card.setClip(clip);

        Region placeholder = new Region();
        placeholder.setStyle("-fx-background-color:#E7E6EE; -fx-background-radius:14;");
        placeholder.maxWidthProperty().bind(card.widthProperty().subtract(28));
        placeholder.maxHeightProperty().bind(card.heightProperty().subtract(28));
        StackPane.setAlignment(placeholder, Pos.CENTER);
        card.getChildren().add(placeholder);

        Label body = new Label();
        body.setManaged(false); body.setVisible(false); body.setWrapText(true);
        textLabels[idx] = body;

        Pane corner = new Pane();
        corner.setPrefSize(30, 20);
        corner.setStyle("-fx-background-color:#E7E6EE; -fx-background-radius:0 16 0 16;");
        StackPane.setAlignment(corner, Pos.TOP_RIGHT);
        StackPane.setMargin(corner, new Insets(8, 8, 0, 0));
        card.getChildren().add(corner);

        wrap.getChildren().addAll(date, card);
        return wrap;
    }

    private void renderEntriesGrid(List<DiaryEntry> entries) {
        ensureGridBuilt();
        for (int i = 0; i < 4; i++) {
            dateLabels[i].setText(i < entries.size() ? entries.get(i).date().format(DAY_FMT) : "");
        }
    }

    /* 회색 영역 꽉 채우는 자동 리사이즈 */
    private void setupBuddyGridAutoResize() {
        if (entriesGrid.getParent() instanceof Region rightPanel &&
            rightPanel.getParent() instanceof ScrollPane sp) {

            sp.setFitToWidth(true);
            sp.setFitToHeight(true);

            sp.viewportBoundsProperty().addListener((o, ov, nv) -> {
                rightPanel.setMinSize(nv.getWidth(), nv.getHeight());
                rightPanel.setPrefSize(nv.getWidth(), nv.getHeight());
            });
        }

        Runnable resize = () -> {
            double cols = 2, rows = 2;
            Insets pad = entriesGrid.getPadding();
            double w = Math.max(0, entriesGrid.getWidth()  - pad.getLeft() - pad.getRight());
            double h = Math.max(0, entriesGrid.getHeight() - pad.getTop()  - pad.getBottom());
            double cellW = (w - entriesGrid.getHgap() * (cols - 1)) / cols;
            double cellH = (h - entriesGrid.getVgap() * (rows - 1)) / rows;

            for (Node n : entriesGrid.getChildren()) {
                if (n instanceof VBox wrap) {
                    wrap.setMinSize(cellW, cellH);
                    wrap.setPrefSize(cellW, cellH);
                    wrap.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
                    GridPane.setHgrow(wrap, Priority.ALWAYS);
                    GridPane.setVgrow(wrap, Priority.ALWAYS);

                    if (wrap.getChildren().size() >= 2) {
                        Node card = wrap.getChildren().get(1);
                        if (card instanceof Region r) {
                            VBox.setVgrow(r, Priority.ALWAYS);
                            r.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
                        }
                    }
                }
            }
        };

        entriesGrid.widthProperty().addListener((o, ov, nv) -> resize.run());
        entriesGrid.heightProperty().addListener((o, ov, nv) -> resize.run());
        javafx.application.Platform.runLater(resize);
    }

    /* 더미 데이터 */
    private List<Buddy> fakeBuddies() {
        return List.of(
            new Buddy("kk", "K.K"),
            new Buddy("naki", "NaKi"),
            new Buddy("guide", "Guide"),
            new Buddy("kk2", "K.K"),
            new Buddy("kk3", "K.K")
        );
    }

    private List<DiaryEntry> fakeEntriesFor(String buddyId) {
        String base = switch (buddyId) {
            case "kk" -> "소리 메모와 곡 아이디어 정리 중.";
            case "naki" -> "UI 스케치/피드백 정리본.";
            case "guide" -> "가이드 문서 초안 업데이트.";
            default -> "하루 기록 메모.";
        };
        return List.of(
            new DiaryEntry(LocalDate.now().minusDays(3), base + " #1"),
            new DiaryEntry(LocalDate.now().minusDays(2), base + " #2"),
            new DiaryEntry(LocalDate.now().minusDays(1), base + " #3"),
            new DiaryEntry(LocalDate.now(), base + " #4")
        );
    }

    private void setupGridConstraints() {
        entriesGrid.getColumnConstraints().clear();
        ColumnConstraints c1 = new ColumnConstraints(); c1.setPercentWidth(50); c1.setHgrow(Priority.ALWAYS);
        ColumnConstraints c2 = new ColumnConstraints(); c2.setPercentWidth(50); c2.setHgrow(Priority.ALWAYS);
        entriesGrid.getColumnConstraints().addAll(c1, c2);
    }

    private void setupRowConstraints() {
        entriesGrid.getRowConstraints().clear();
        RowConstraints r1 = new RowConstraints(); r1.setPercentHeight(50); r1.setVgrow(Priority.ALWAYS);
        RowConstraints r2 = new RowConstraints(); r2.setPercentHeight(50); r2.setVgrow(Priority.ALWAYS);
        entriesGrid.getRowConstraints().addAll(r1, r2);
    }

    private List<Buddy> fetchBuddiesFromDB() { return Collections.emptyList(); }
    private List<DiaryEntry> fetchEntriesFromDB(String buddyId) { return Collections.emptyList(); }

    private record Buddy(String id, String name) {}
    private record DiaryEntry(LocalDate date, String text) {}
}

package com.share.dairy.controller;

import com.share.dairy.model.diary.DiaryEntry;
import com.share.dairy.service.diary.DiaryWriteService;

import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.*;
import javafx.scene.shape.Rectangle;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Buddy Diary – 보기 전용(다른 사람이 쓴 일기만 표시) */
public class BuddyDiaryController {

    // FXML
    @FXML private GridPane entriesGrid;   // 우측 2×2 그리드
    @FXML private VBox buddyList;         // 좌측 버디 리스트

    // 상태
    private String selectedBuddyId;
    private boolean gridInitialized = false;

    // 셀 참조(표시/모달용)
    private final Label[] dateLabels     = new Label[4];
    private final Label[] previewLabels  = new Label[4];
    private final PreviewEntry[] cellData = new PreviewEntry[4];

    private static final DateTimeFormatter DAY_FMT = DateTimeFormatter.ofPattern("yyyy.MM.dd");

    // DB 서비스(조회만 사용)
    private final DiaryWriteService diaryWriteService = new DiaryWriteService();

    // ────────────────────────────────────────────────────────────────────────────

    @FXML
    public void initialize() {
        // ESC 무력화 + 버튼 크기 고정(눌림 변형 방지)
        entriesGrid.sceneProperty().addListener((obs, o, s) -> {
            if (s != null) {
                s.addEventFilter(KeyEvent.KEY_PRESSED, e -> { if (e.getCode() == KeyCode.ESCAPE) e.consume(); });
                freezeAllButtonSizesOnce(s);
            }
        });

        // 그리드 기본(가로만 부모에 맞추고 세로는 스크롤)
        entriesGrid.setHgap(18);
        entriesGrid.setVgap(18);
        entriesGrid.setPadding(new Insets(16));
        setupGridConstraints();
        setupRowConstraints();
        bindGridToParent();

        // 좌측 리스트
        buddyList.setAlignment(Pos.TOP_CENTER);
        buddyList.setFillWidth(true);
        buddyList.setSpacing(12);
        buddyList.setPadding(new Insets(0, 10, 0, 10));

        // ▶ 여기서는 데모 버디만 넣어둠.
        //    실제 연결 시, id에 **해당 버디의 user_id(숫자 문자열)** 를 넣어주세요.
        renderBuddyList(fakeBuddies());

        // 2×2 셀 생성
        ensureGridBuilt();

        // 첫 선택(데모용)
        if (!buddyList.getChildren().isEmpty()) {
            Object firstId = buddyList.getChildren().getFirst().getUserData();
            if (firstId != null) selectBuddy(String.valueOf(firstId));
        }
    }

    // ───────────────────────── 좌측 리스트 ─────────────────────────
    private void renderBuddyList(List<Buddy> buddies) {
        buddyList.getChildren().clear();
        for (Buddy b : buddies) buddyList.getChildren().add(buildBuddyItem(b));
    }

    private Node buildBuddyItem(Buddy b) {
        HBox card = new HBox(12);
        card.setAlignment(Pos.CENTER_LEFT);
        card.setPadding(new Insets(16));
        card.setMinHeight(64); card.setPrefHeight(68);
        card.setMaxWidth(Double.MAX_VALUE);

        final String BASE   = "-fx-background-color:#CBAFD1; -fx-background-radius:14;";
        final String HILITE = "-fx-background-color:white; -fx-background-radius:14; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.18), 8, 0, 0, 3);";
        card.setStyle(BASE);

        Node avatar = loadAvatar(b.id());
        Label nameLabel = new Label(b.name());
        nameLabel.setStyle("-fx-font-size:17; -fx-font-weight:bold; -fx-text-fill:#141414;");
        card.getChildren().addAll(avatar, nameLabel);

        double gutter = buddyList.getPadding().getLeft();
        StackPane slot = new StackPane(card);
        slot.setAlignment(Pos.CENTER);
        StackPane.setMargin(card, new Insets(0, gutter, 0, gutter));
        card.maxWidthProperty().bind(slot.widthProperty().subtract(gutter * 2));

        // ▼ 이 userData가 selectBuddy로 그대로 전달됩니다.
        //    실제 환경에서는 b.id()에 "실제 buddy의 user_id(문자열)" 를 넣어주세요.
        slot.setUserData(b.id());

        slot.setOnMouseClicked(e -> selectBuddy(b.id()));
        slot.setOnMouseEntered(e -> card.setStyle(HILITE));
        slot.setOnMouseExited(e -> card.setStyle(
                Objects.equals(slot.getUserData(), selectedBuddyId) ? HILITE : BASE
        ));
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
        } catch (Exception ignore) { return new Label(); }
    }

    private void selectBuddy(String buddyId) {
        this.selectedBuddyId = buddyId;

        // 좌측 하이라이트
        final String BASE = "-fx-background-color:#CBAFD1; -fx-background-radius:14;";
        final String HILITE = "-fx-background-color:white; -fx-background-radius:14; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.18), 8, 0, 0, 3);";
        for (Node slot : buddyList.getChildren()) {
            Node card = slot instanceof Pane p && !p.getChildren().isEmpty() ? p.getChildren().get(0) : slot;
            card.setStyle(Objects.equals(slot.getUserData(), buddyId) ? HILITE : BASE);
        }

        // ▶ DB에서 해당 버디(user_id)의 최신 4건을 읽어 2×2에 매핑
        renderFromDB(buddyId);
    }

    // ───────────────────────── 2×2 셀 구성/렌더 ─────────────────────────
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
        VBox wrap = new VBox(5);
        wrap.setPrefHeight(210);
        wrap.setFillWidth(true);
        wrap.setMaxWidth(Double.MAX_VALUE);
        VBox.setVgrow(wrap, Priority.ALWAYS);

        // 날짜
        Label date = new Label();
        date.setStyle("-fx-font-size:14; -fx-text-fill:#4a4a4a; -fx-padding:0 0 6 4;");
        dateLabels[idx] = date;

        // 카드(흰 배경 + 그림자)
        StackPane card = new StackPane();
        card.setPrefHeight(170);
        card.setStyle(
            "-fx-background-color:white;" +
            "-fx-background-radius:16;" +
            "-fx-effect:dropshadow(gaussian, rgba(0,0,0,0.12), 10, 0.2, 0, 2);" +
            "-fx-padding:12;"
        );
        card.setCursor(Cursor.HAND);

        // 둥근 모서리 클리핑
        Rectangle clip = new Rectangle();
        clip.setArcWidth(16); clip.setArcHeight(16);
        card.layoutBoundsProperty().addListener((o, ov, nv) -> {
            clip.setWidth(nv.getWidth());
            clip.setHeight(nv.getHeight());
        });
        card.setClip(clip);

        // 미리보기 라벨(스크롤 없음)
        Label preview = new Label();
        preview.setWrapText(true);
        preview.setStyle("-fx-font-size:12; -fx-text-fill:#222;");
        card.widthProperty().addListener((o, ov, nv) -> preview.setMaxWidth(nv.doubleValue() - 20));
        preview.setMouseTransparent(true);
        previewLabels[idx] = preview;
        card.getChildren().add(preview);

        // 클릭 → 보기 전용 모달
        card.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() >= 1) {
                openViewerModal(idx);
            }
        });

        wrap.getChildren().addAll(date, card);
        return wrap;
    }

    private void renderEntriesGrid(List<PreviewEntry> entries) {
        for (int i = 0; i < 4; i++) {
            PreviewEntry e = entries.get(i);
            cellData[i] = e;
            dateLabels[i].setText(e.date() != null ? e.date().format(DAY_FMT) : "");
            previewLabels[i].setText(tidy(e.text(), 140)); // 썸네일은 요약
        }
    }

    /** DB에서 불러와 2×2에 매핑 */
    private void renderFromDB(String buddyId) {
        Long userId = parseUserId(buddyId);
        List<PreviewEntry> four = new ArrayList<>(4);
        try {
            if (userId != null) {
                List<DiaryEntry> list = diaryWriteService.loadMyDiaryList(userId);
                // 최신 4개만, 부족하면 빈칸 채우기
                for (int i = 0; i < 4; i++) {
                    if (i < list.size()) {
                        DiaryEntry d = list.get(i);
                        four.add(new PreviewEntry(
                            d.getEntryId(),
                            d.getEntryDate(),
                            nvl(d.getTitle()),
                            nvl(d.getDiaryContent())
                        ));
                    } else {
                        four.add(new PreviewEntry(null, null, "", "")); // 빈 칸
                    }
                }
            } else {
                // userId 파싱 실패 → 빈칸
                for (int i = 0; i < 4; i++) four.add(new PreviewEntry(null, null, "", ""));
            }
        } catch (RuntimeException ex) {
            // 조회 실패해도 화면은 유지
            for (int i = 0; i < 4; i++) four.add(new PreviewEntry(null, null, "", ""));
        }
        renderEntriesGrid(four);
    }

    // ───────────────────────── 보기 전용 모달 ─────────────────────────
    private void openViewerModal(int idx) {
        PreviewEntry cur = cellData[idx];
        LocalDate date = (cur != null && cur.date() != null)
                ? cur.date()
                : parseDateLabelSafe(dateLabels[idx].getText());
        String title = (cur != null ? nvl(cur.title()) : "");
        String text  = (cur != null ? nvl(cur.text())  : "");

        Stage dlg = new Stage();
        if (entriesGrid.getScene() != null) dlg.initOwner(entriesGrid.getScene().getWindow());
        dlg.initModality(Modality.APPLICATION_MODAL);
        dlg.setTitle(date != null ? date.format(DAY_FMT) : "Diary");

        Label dateLbl = new Label(date != null ? date.format(DAY_FMT) : "");
        dateLbl.setStyle("-fx-font-size:16; -fx-font-weight:bold;");

        Label titleLbl = new Label(title.isBlank() ? "(제목 없음)" : title);
        titleLbl.setStyle("-fx-font-size:14; -fx-font-weight:bold;");

        TextArea content = new TextArea(text);
        content.setEditable(false);
        content.setWrapText(true);
        content.setFocusTraversable(false);
        content.setPrefRowCount(16);

        Button close = new Button("닫기");
        close.setOnAction(ev -> dlg.close());

        VBox root = new VBox(12, dateLbl, titleLbl, content, close);
        root.setPadding(new Insets(16));
        dlg.setScene(new Scene(root, 720, 560));
        dlg.showAndWait();
    }

    // ───────────────────────── 레이아웃/유틸 ─────────────────────────
    private void bindGridToParent() {
        if (entriesGrid.getParent() instanceof Region prGrid) {
            entriesGrid.prefWidthProperty().bind(prGrid.widthProperty()); // width only (세로 스크롤 유지)
        } else {
            entriesGrid.parentProperty().addListener((o, oldP, p) -> {
                if (p instanceof Region prGrid2) {
                    entriesGrid.prefWidthProperty().bind(prGrid2.widthProperty());
                }
            });
        }
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

    // ───────────────────────── 데모 버디 목록 ─────────────────────────
    private List<Buddy> fakeBuddies() {


        // 중요: id 자리에 **실제 user_id(문자열)** 를 넣어야 DB 조회가 됩니다.
        // 지금은 예시로 17, 23, 42, 58, 61을 넣어둡니다.
        // *************************
        // 백엔드랑 연결
        return List.of(
            new Buddy("17", "K.K"), // ← 친구 A의 user_id
            new Buddy("23", "NaKi"), // ← 친구 B의 user_id
            new Buddy("42", "Guide"), // ← 친구 C의 user_id
            new Buddy("58", "K.K"),
            new Buddy("61", "K.K")
        );
    }

    private Long parseUserId(String buddyId) {
        try { return Long.parseLong(buddyId); } catch (Exception e) { return null; }
    }

    // ───────────────────────── 헬퍼/모델 ─────────────────────────
    private static String tidy(String s, int limit) {
        String one = nvl(s).replace("\r", " ").replace("\n", " ").trim();
        return one.length() > limit ? one.substring(0, limit) + "…" : one;
    }
    private static String nvl(String s) { return s == null ? "" : s; }

    private static LocalDate parseDateLabelSafe(String label) {
        try { return (label == null || label.isBlank()) ? LocalDate.now() : LocalDate.parse(label, DAY_FMT); }
        catch (Exception e) { return LocalDate.now(); }
    }

    private record Buddy(String id, String name) {}
    private record PreviewEntry(Long id, LocalDate date, String title, String text) {}
}

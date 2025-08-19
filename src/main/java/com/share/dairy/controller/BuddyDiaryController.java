package com.share.dairy.controller;

import com.share.dairy.model.diary.DiaryEntry;          // 실제 DB 모델
import com.share.dairy.model.enums.Visibility;
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

/**
 * Buddy 화면 – 2x2 썸네일 + 모달 편집(Title/Contents).
 * 지금은 FAKE_DATA=true 로 메모리 저장만. DB는 나중에 스위치.
 */
public class BuddyDiaryController {

    // FXML
    @FXML private GridPane entriesGrid;   // 우측 2×2 그리드
    @FXML private VBox buddyList;         // 좌측 버디 리스트

    // 상태
    private String selectedBuddyId;
    private boolean gridInitialized = false;

    // 셀 참조
    private final Label[] dateLabels     = new Label[4];
    private final Label[] previewLabels  = new Label[4];
    private final PreviewEntry[] cellData = new PreviewEntry[4]; // 현재 4칸 데이터

    // 설정
    private static final boolean FAKE_DATA = true; // DB 붙일 땐 false
    private static final DateTimeFormatter DAY_FMT = DateTimeFormatter.ofPattern("yyyy.MM.dd");

    // 메모리 저장소: 버디ID → 그 주 4칸
    private final Map<String, List<PreviewEntry>> demoStore = new HashMap<>();

    // 서비스(DB 모드에서만 사용)
    private final DiaryWriteService diaryWriteService = new DiaryWriteService();

    // ────────────────────────────────────────────────────────────────────────────

    @FXML
    public void initialize() {
        // ESC 무력화 + 버튼 크기 고정
        entriesGrid.sceneProperty().addListener((obs, o, s) -> {
            if (s != null) {
                s.addEventFilter(KeyEvent.KEY_PRESSED, e -> { if (e.getCode() == KeyCode.ESCAPE) e.consume(); });
                freezeAllButtonSizesOnce(s);
            }
        });

        // 그리드 기본
        entriesGrid.setHgap(18);
        entriesGrid.setVgap(18);
        entriesGrid.setPadding(new Insets(16));
        setupGridConstraints();
        setupRowConstraints();
        bindGridToParent(); // 가로만 바인딩(세로 스크롤 유지)

        // 좌측 리스트
        buddyList.setAlignment(Pos.TOP_CENTER);
        buddyList.setFillWidth(true);
        buddyList.setSpacing(12);
        buddyList.setPadding(new Insets(0, 10, 0, 10));

        // 더미 버디 렌더
        List<Buddy> buddies = fakeBuddies();
        renderBuddyList(buddies);

        // 2×2 셀 생성
        ensureGridBuilt();

        // 첫 선택
        if (!buddies.isEmpty()) selectBuddy(buddies.get(0).id());
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
        } catch (Exception ignore) { return new Label(); }
    }

    private void selectBuddy(String buddyId) {
        this.selectedBuddyId = buddyId;

        final String BASE = "-fx-background-color:#CBAFD1; -fx-background-radius:14;";
        final String HILITE = "-fx-background-color:white; -fx-background-radius:14; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.18), 8, 0, 0, 3);";
        for (Node slot : buddyList.getChildren()) {
            Node card = slot instanceof Pane p && !p.getChildren().isEmpty() ? p.getChildren().get(0) : slot;
            card.setStyle(Objects.equals(slot.getUserData(), buddyId) ? HILITE : BASE);
        }

        List<PreviewEntry> entries = FAKE_DATA ? getOrInitDemoList(buddyId) : mapFromDB(buddyId);
        renderEntriesGrid(entries);
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

        // 미리보기 라벨
        Label preview = new Label();
        preview.setWrapText(true);
        preview.setStyle("-fx-font-size:12; -fx-text-fill:#222;");
        card.widthProperty().addListener((o, ov, nv) -> preview.setMaxWidth(nv.doubleValue() - 20));
        preview.setMouseTransparent(true);
        previewLabels[idx] = preview;
        card.getChildren().add(preview);

        // 클릭/더블클릭 → 편집 모달
        card.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() >= 1) openEditorModal(idx);
        });

        wrap.getChildren().addAll(date, card);
        return wrap;
    }

    private void renderEntriesGrid(List<PreviewEntry> entries) {
        for (int i = 0; i < 4; i++) {
            PreviewEntry e = entries.get(i);
            cellData[i] = e;
            dateLabels[i].setText(e.date() != null ? e.date().format(DAY_FMT) : "");
            previewLabels[i].setText(tidy(e.text(), 140)); // 썸네일은 contents 요약
        }
    }

    // ───────────────────────── 편집/작성 모달 (MY DIARY 스타일) ─────────────────────────
    private void openEditorModal(int idx) {
        PreviewEntry cur = cellData[idx];

        LocalDate date = (cur != null && cur.date() != null)
                ? cur.date()
                : parseDateLabelSafe(dateLabels[idx].getText());

        Stage dlg = new Stage();
        if (entriesGrid.getScene() != null) dlg.initOwner(entriesGrid.getScene().getWindow());
        dlg.initModality(Modality.APPLICATION_MODAL);
        dlg.setTitle(date != null ? date.format(DAY_FMT) : "Diary");

        // 상단 날짜
        Label dateLbl = new Label(date != null ? date.format(DAY_FMT) : "");
        dateLbl.setStyle("-fx-font-size:16; -fx-font-weight:bold;");

        // MY DIARY 폼처럼: TITLE + CONTENTS
        TextField titleField = new TextField(cur != null ? nvl(cur.title()) : "");
        titleField.setPromptText("TITLE");

        TextArea contentArea = new TextArea(cur != null ? nvl(cur.text()) : "");
        contentArea.setPromptText("CONTENTS");
        contentArea.setWrapText(true);
        contentArea.setPrefRowCount(16);

        Button save = new Button("저장");
        Button cancel = new Button("닫기");
        HBox btns = new HBox(8, save, cancel);

        VBox form = new VBox(10,
                dateLbl,
                titleField,
                contentArea,
                btns
        );
        form.setPadding(new Insets(16));

        dlg.setScene(new Scene(form, 720, 560));

        // 저장: 메모리 저장소 업데이트 → 썸네일 즉시 반영
        save.setOnAction(ev -> {
            String title = nvl(titleField.getText()).trim();
            String text  = nvl(contentArea.getText()).trim();
            LocalDate d  = (date != null ? date : LocalDate.now());

            if (FAKE_DATA) {
                List<PreviewEntry> list = getOrInitDemoList(selectedBuddyId);
                PreviewEntry updated = new PreviewEntry(null, d, title, text);
                list.set(idx, updated);     // 현재 칸 교체
                cellData[idx] = updated;

                // 썸네일 갱신
                dateLabels[idx].setText(d.format(DAY_FMT));
                previewLabels[idx].setText(tidy(text, 140));

                dlg.close();
                return;
            }

            // ─ DB 붙일 때(타이틀도 저장) ─
            try {
                if (cur != null && cur.id() != null) {
                    // 현재 DiaryEntryDao에는 content만 update가 있으니, title은 나중에 추가 예정.
                    diaryWriteService.updateContent(cur.id(), text);
                } else {
                    long uid = resolveUserId(selectedBuddyId);
                    DiaryEntry e = new DiaryEntry();
                    e.setUserId(uid);
                    e.setEntryDate(d);
                    e.setTitle(title);
                    e.setDiaryContent(text);
                    e.setVisibility(Visibility.PRIVATE);
                    long newId = diaryWriteService.create(e);
                    cellData[idx] = new PreviewEntry(newId, d, title, text);
                }
                dateLabels[idx].setText(d.format(DAY_FMT));
                previewLabels[idx].setText(tidy(text, 140));
                dlg.close();
            } catch (Exception ex) {
                new Alert(Alert.AlertType.ERROR, "저장 실패: " + ex.getMessage()).showAndWait();
            }
        });

        cancel.setOnAction(ev -> dlg.close());
        dlg.showAndWait();
    }

    // ───────────────────────── 레이아웃/유틸 ─────────────────────────
    private void bindGridToParent() {
        if (entriesGrid.getParent() instanceof Region prGrid) {
            entriesGrid.prefWidthProperty().bind(prGrid.widthProperty());
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
            for (Node n : root.lookupAll(".text-area")) {
                if (n instanceof TextArea ta) {
                    ta.setEditable(false);
                    ta.setMouseTransparent(true);
                }
            }
        });
    }

    // ───────────────────────── 더미 데이터/저장소 ─────────────────────────
    private List<Buddy> fakeBuddies() {
        return List.of(
            new Buddy("kk", "K.K"),
            new Buddy("naki", "NaKi"),
            new Buddy("guide", "Guide"),
            new Buddy("kk2", "K.K"),
            new Buddy("kk3", "K.K")
        );
    }

    /** 버디의 4칸 데이터를 저장소에서 가져오거나, 없으면 만들어 저장 */
    private List<PreviewEntry> getOrInitDemoList(String buddyId) {
        return demoStore.computeIfAbsent(buddyId, id -> {
            String base = switch (id) {
                case "kk"   -> "날씨가 너무 덥군!";
                case "naki" -> "아이스 아메리카노 땡긴다!";
                case "guide"-> "바다 갔다 왔어요!";
                default     -> "하루 기록 메모!";
            };
            return new ArrayList<>(List.of(
                new PreviewEntry(null, LocalDate.now().minusDays(3), "메모 #1", base + " #1 테스트용 코드"),
                new PreviewEntry(null, LocalDate.now().minusDays(2), "메모 #2", base + " #2 테스트용 코드"),
                new PreviewEntry(null, LocalDate.now().minusDays(1), "메모 #3", base + " #3 테스트용 코드"),
                new PreviewEntry(null, LocalDate.now(),            "메모 #4", base + " #4 테스트용 코드")
            ));
        });
    }

    // DB 모드일 때만 사용(지금은 안 씀)
    private List<PreviewEntry> mapFromDB(String buddyId) {
        long uid = resolveUserId(buddyId);
        if (uid <= 0) return List.of(
            new PreviewEntry(null, LocalDate.now().minusDays(3), "", ""),
            new PreviewEntry(null, LocalDate.now().minusDays(2), "", ""),
            new PreviewEntry(null, LocalDate.now().minusDays(1), "", ""),
            new PreviewEntry(null, LocalDate.now(),            "", "")
        );
        try {
            List<DiaryEntry> list = diaryWriteService.loadMyDiaryList(uid);
            List<PreviewEntry> out = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                if (i < list.size()) {
                    DiaryEntry e = list.get(i);
                    out.add(new PreviewEntry(e.getEntryId(), e.getEntryDate(), nvl(e.getTitle()), nvl(e.getDiaryContent())));
                } else {
                    out.add(new PreviewEntry(null, LocalDate.now().minusDays(3 - i), "", ""));
                }
            }
            return out;
        } catch (RuntimeException ex) {
            return List.of(
                new PreviewEntry(null, LocalDate.now().minusDays(3), "", ""),
                new PreviewEntry(null, LocalDate.now().minusDays(2), "", ""),
                new PreviewEntry(null, LocalDate.now().minusDays(1), "", ""),
                new PreviewEntry(null, LocalDate.now(),            "", "")
            );
        }
    }

    private long resolveUserId(String buddyId) {
        try { return Long.parseLong(buddyId); } catch (Exception e) { return 0L; }
    }

    // ───────────────────────── 헬퍼 ─────────────────────────
    private static String tidy(String s, int limit) {
        String one = nvl(s).replace("\r", " ").replace("\n", " ").trim();
        return one.length() > limit ? one.substring(0, limit) + "…" : one;
    }
    private static String nvl(String s) { return s == null ? "" : s; }

    private static LocalDate parseDateLabelSafe(String label) {
        try { return (label == null || label.isBlank()) ? LocalDate.now() : LocalDate.parse(label, DAY_FMT); }
        catch (Exception e) { return LocalDate.now(); }
    }

    // 뷰용 내부 레코드 (id: DB모드에서 사용)
    private record Buddy(String id, String name) {}
    private record PreviewEntry(Long id, LocalDate date, String title, String text) {}
}

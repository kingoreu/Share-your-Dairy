package com.share.dairy.controller;

import com.share.dairy.auth.UserSession;                // ✅ 현재 로그인 사용자
import com.share.dairy.dao.friend.FriendshipDao;        // ✅ 친구 목록 DAO
import com.share.dairy.model.friend.Friendship;         // ✅ 친구 관계 엔티티
import com.share.dairy.model.diary.DiaryEntry;          // ✅ 일기 엔티티
import com.share.dairy.model.enums.Visibility;          // ✅ PRIVATE 필터링용
import com.share.dairy.model.enums.CharacterType;       // ⭕ 아바타 타입(없으면 null)
import com.share.dairy.service.diary.DiaryWriteService; // ✅ 일기 조회 서비스

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
import javafx.scene.input.MouseButton;
import javafx.scene.layout.*;
import javafx.scene.shape.Rectangle;
import javafx.stage.Modality;
import javafx.stage.Stage;
import java.util.Locale;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * BuddyDiaryController
 * -----------------------------------------------------------------------
 * 좌측: "승인된 친구(ACCEPTED)" 목록을 카드로 표시
 * 우측: 선택한 친구의 글을 최신순으로 보여줌
 *  - 첫 화면 4개
 *  - 스크롤을 바닥 근처로 내리면 다음 N개를 동적 추가 (무한 스크롤 느낌)
 *
 * ⚠ FXML에서 우측 ScrollPane에 fx:id="entriesScroll" 한 줄만 추가하면 됨.
 *   그 외는 전부 이 컨트롤러 코드로 처리.
 */
public class BuddyDiaryController {

    /* ============================
     * FXML 노드
     * ============================ */
    @FXML private ScrollPane entriesScroll; // 우측 목록 스크롤 (무한 스크롤 트리거)
    @FXML private GridPane entriesGrid;     // 우측 2열 그리드 (셀을 동적으로 추가)
    @FXML private VBox buddyList;           // 좌측 친구 리스트(ScrollPane content)

    /* ============================
     * 상태/상수
     * ============================ */
    private String selectedBuddyId;                      // 현재 선택된 친구의 user_id(문자열)
    private boolean scrollHooked = false;                // 스크롤 리스너 중복 방지

    // 무한 스크롤 상태
    private final List<PreviewEntry> allEntries = new ArrayList<>(); // 선택 친구의 전체 글(필터/정렬 후)
    private int renderedCount = 0;                                    // 그리드에 그린 개수
    private static final int FIRST_PAGE = 4;                          // 첫 화면 개수
    private static final int NEXT_PAGE  = 8;                          // 이후 스크롤 시 추가 개수

    private static final DateTimeFormatter DAY_FMT = DateTimeFormatter.ofPattern("yyyy.MM.dd");

    /* ============================
     * 서비스/DAO
     * ============================ */
    private final DiaryWriteService diaryWriteService = new DiaryWriteService();
    private final FriendshipDao friendshipDao = new FriendshipDao();

    /* =======================================================================
     * 라이프사이클
     * ======================================================================= */
    @FXML
    public void initialize() {
        // ESC 키로 모달이 비정상 종료되는 걸 예방 + 버튼 눌림시 크기 튐 방지
        entriesGrid.sceneProperty().addListener((obs, oldScene, scene) -> {
            if (scene != null) {
                scene.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED,
                        e -> { if (e.getCode() == KeyCode.ESCAPE) e.consume(); });
                freezeAllButtonSizesOnce(scene);
            }
        });

        // 우측 그리드: 2열 고정, 행 제약은 두지 않음(행이 동적으로 늘어나도록)
        setupGridColumnsOnly();
        bindGridWidthToParent();

        // 좌측 친구 리스트 스타일
        buddyList.setAlignment(Pos.TOP_CENTER);
        buddyList.setFillWidth(true);
        buddyList.setSpacing(12);
        buddyList.setPadding(new Insets(0, 10, 0, 10));

        // 좌측: DB에서 친구 목록 로드 → 렌더링
        renderBuddyList(loadBuddiesFromDB());

        // 우측: 무한 스크롤 리스너(한 번만 설치)
        hookInfiniteScrollOnce();

        // 첫 친구 자동 선택
        if (!buddyList.getChildren().isEmpty()) {
            Object firstId = buddyList.getChildren().getFirst().getUserData();
            if (firstId != null) selectBuddy(String.valueOf(firstId));
        }
    }

    /* =======================================================================
     * 좌측: 친구 리스트
     * ======================================================================= */

    /**
     * DB에서 "ACCEPTED" 친구 목록을 읽어 화면 모델(Buddy)로 변환.
     * 현재 Dao는 닉네임/캐릭터를 안 실어오므로 이름은 "USER {id}" 로 폴백.
     * (아래 '선택 개선' 섹션에 users 조인 방법을 제공)
     */
    private List<Buddy> loadBuddiesFromDB() {
        long myId = UserSession.requireId();
        List<FriendshipDao.FriendSummary> rows;
        try {
            rows = friendshipDao.findAcceptedSummariesFor(myId);
        } catch (Exception e) {
            System.err.println("[BuddyDiary] 친구 요약 로드 실패: " + e.getMessage());
            rows = List.of();
        }

        return rows.stream()
                .map(s -> new Buddy(
                        String.valueOf(s.buddyId),
                        (s.nickname != null && !s.nickname.isBlank()) ? s.nickname : ("USER " + s.buddyId),
                        (s.characterType != null) ? safeEnum(s.characterType) : null
                ))
                .sorted(Comparator.comparing(Buddy::name, String.CASE_INSENSITIVE_ORDER))
                .collect(Collectors.toList());
    }

    // 기존 safeEnum 대체
    private static CharacterType safeEnum(String dbValue) {
        try { return CharacterType.valueOf(dbValue.toUpperCase(Locale.ROOT)); }
        catch (Exception ignore) { return null; }
    }

    /** VBox 컨테이너(buddyList)에 친구 카드들을 렌더링 */
    private void renderBuddyList(List<Buddy> buddies) {
        buddyList.getChildren().clear();
        for (Buddy b : buddies) buddyList.getChildren().add(buildBuddyItem(b));
    }

    /** 친구 카드 한 줄 생성 */
    private Node buildBuddyItem(Buddy b) {
        HBox card = new HBox(12);
        card.setAlignment(Pos.CENTER_LEFT);
        card.setPadding(new Insets(16));
        card.setMinHeight(64);
        card.setPrefHeight(68);
        card.setMaxWidth(Double.MAX_VALUE);

        final String BASE   = "-fx-background-color:#CBAFD1; -fx-background-radius:14;";
        final String HILITE = "-fx-background-color:white; -fx-background-radius:14; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.18), 8, 0, 0, 3);";
        card.setStyle(BASE);

        Node avatar = loadAvatar(b); // 캐릭터 타입 기반(없으면 폴백)
        Label nameLabel = new Label(b.name());
        nameLabel.setStyle("-fx-font-size:17; -fx-font-weight:bold; -fx-text-fill:#141414;");
        card.getChildren().addAll(avatar, nameLabel);

        // 좌우 여백 맞추기 위해 감싸는 슬롯 사용
        double gutter = buddyList.getPadding().getLeft();
        StackPane slot = new StackPane(card);
        slot.setAlignment(Pos.CENTER);
        StackPane.setMargin(card, new Insets(0, gutter, 0, gutter));
        card.maxWidthProperty().bind(slot.widthProperty().subtract(gutter * 2));

        // 클릭 시 selectBuddy()로 전달할 값(친구 user_id 문자열)
        slot.setUserData(b.id());

        slot.setOnMouseClicked(e -> selectBuddy(b.id()));
        slot.setOnMouseEntered(e -> card.setStyle(HILITE));
        slot.setOnMouseExited(e -> card.setStyle(
                Objects.equals(slot.getUserData(), selectedBuddyId) ? HILITE : BASE
        ));
        return slot;
    }

    /** 아바타 로딩: CharacterType → /images/characters/{TYPE}.png, 없으면 폴백 */
    /** 아바타 로딩: CharacterType → /character/{TYPE}.png, 없으면 폴백 */
    private Node loadAvatar(Buddy b) {
        try {
            Image img;
            if (b.ctype() != null) {
                // ✅ 리소스 경로 수정 (resources 접두사 X, 폴더명 character)
                String p = "/character/" + b.ctype().name().toLowerCase(Locale.ROOT) + ".png";
                // 파일명이 소문자라면 ↓로 바꿔 쓰세요
                // String p = "/character/" + b.ctype().name().toLowerCase(Locale.ROOT) + ".png";

                img = new Image(Objects.requireNonNullElse(
                        getClass().getResourceAsStream(p),
                        // ✅ 폴백도 같은 폴더에 두기
                        getClass().getResourceAsStream("/character/_fallback.png")
                ));
            } else {
                // 사용자별 이미지가 있으면 사용, 없으면 캐릭터 폴더 폴백
                img = new Image(Objects.requireNonNullElse(
                        getClass().getResourceAsStream("/images/buddy/" + b.id() + ".png"),
                        getClass().getResourceAsStream("/character/_fallback.png")
                ));
            }
            ImageView iv = new ImageView(img);

            iv.setPreserveRatio(true);
            iv.setSmooth(true);
            iv.setFitHeight(36);         // 한쪽만
            // 🔧 클립 초기 크기를 36x36으로 먼저 줘서 0클립을 방지
            Rectangle clip = new Rectangle(36, 36);
            clip.setArcWidth(16);
            clip.setArcHeight(16);
            iv.setClip(clip);
            // 이후 실제 렌더 크기에 맞춰 클립을 업데이트
            iv.layoutBoundsProperty().addListener((o, ov, nv) -> {
                clip.setWidth(nv.getWidth());
                clip.setHeight(nv.getHeight());
            });
            return iv;

        } catch (Exception ignore) {
            // 마지막 안전망: 폴백
            ImageView iv = new ImageView(new Image(
                    Objects.requireNonNull(getClass().getResourceAsStream("/character/_fallback.png"))
            ));
            iv.setFitWidth(36); iv.setFitHeight(36);
            Rectangle clip = new Rectangle(36, 36); clip.setArcWidth(36); clip.setArcHeight(36);
            iv.setClip(clip);
            return iv;
        }
    }


    /* =======================================================================
     * 좌측 카드 클릭 → 우측: 무한 스크롤 목록 초기화/렌더
     * ======================================================================= */
    private void selectBuddy(String buddyId) {
        this.selectedBuddyId = buddyId;

        // 좌측 하이라이트 갱신
        final String BASE = "-fx-background-color:#CBAFD1; -fx-background-radius:14;";
        final String HILITE = "-fx-background-color:white; -fx-background-radius:14; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.18), 8, 0, 0, 3);";
        for (Node slot : buddyList.getChildren()) {
            Node card = slot instanceof Pane p && !p.getChildren().isEmpty() ? p.getChildren().get(0) : slot;
            card.setStyle(Objects.equals(slot.getUserData(), buddyId) ? HILITE : BASE);
        }

        // 1) 선택된 친구의 "전체 글"을 메모리에 로드(가시성/최신순)
        loadAllEntriesForBuddy(buddyId);

        // 2) 그리드 초기화 → 처음 4개 렌더
        entriesGrid.getChildren().clear();
        renderedCount = 0;
        renderNextPage(FIRST_PAGE);

        // 3) 스크롤 맨 위로
        if (entriesScroll != null) entriesScroll.setVvalue(0);

        // ✅ 초기 4개만으로 화면이 남으면 자동으로 더 채움
        ensureViewportFilledLater();
    }

    /** 콘텐츠 높이가 뷰포트보다 작으면 NEXT_PAGE씩 계속 추가해서 꽉 채움(또는 더 이상 없음). */
    private void fillViewportIfShort() {
        if (entriesScroll == null) return;

        // ScrollPane 내부 콘텐츠(AnchorPane) 안의 GridPane 높이로 판단
        double viewportH = entriesScroll.getViewportBounds().getHeight();
        double contentH  = entriesGrid.getBoundsInParent().getHeight(); // grid 자체의 보이는 높이

        // 콘텐츠가 뷰포트보다 작고, 아직 남은 데이터가 있으면 계속 추가
        while (renderedCount < allEntries.size() && contentH <= viewportH + 1) {
            renderNextPage(Math.min(NEXT_PAGE, allEntries.size() - renderedCount));
            // 레이아웃 갱신 후 높이 다시 측정
            entriesGrid.applyCss(); entriesGrid.layout();
            contentH = entriesGrid.getBoundsInParent().getHeight();
        }
    }

    /** 초기 렌더 직후 레이아웃이 잡힌 다음, 화면이 비어보이면 자동으로 더 렌더한다. */
    private void ensureViewportFilledLater() {
        javafx.application.Platform.runLater(() -> {
            fillViewportIfShort();
        });
    }


    /** 선택된 친구의 전체 글을 메모리에 로드(가시성/최신순 정렬) */
    private void loadAllEntriesForBuddy(String buddyId) {
        allEntries.clear();
        Long userId = parseUserId(buddyId);
        if (userId == null) return;

        try {
            // ⚠ DiaryWriteService.loadMyDiaryList(userId) 가 userId 인자를 무시하지 않도록 아래 '필수 패치' 적용 필요
            // var list = diaryWriteService.loadMyDiaryList(userId);
            var list = diaryWriteService.loadUserDiaryList(userId);

            list.stream()
                    .filter(d -> d.getVisibility() != Visibility.PRIVATE) // 친구/공개만
                    .sorted(Comparator
                            .comparing(DiaryEntry::getEntryDate, Comparator.nullsLast(Comparator.naturalOrder()))
                            .thenComparing(DiaryEntry::getEntryId, Comparator.nullsLast(Comparator.naturalOrder()))
                            .reversed() // 최신순
                    )
                    .map(d -> new PreviewEntry(
                            d.getEntryId(),
                            d.getEntryDate(),
                            nvl(d.getTitle()),
                            nvl(d.getDiaryContent())
                    ))
                    .forEach(allEntries::add);

        } catch (RuntimeException ex) {
            System.err.println("[BuddyDiary] 일기 로드 실패: " + ex.getMessage());
        }
    }

    /* =======================================================================
     * 우측: 동적 셀 렌더링(2열) + 무한 스크롤
     * ======================================================================= */

    /** 그리드에 다음 N개 셀을 추가(2열 유지, 행은 자동 증가) */
    private void renderNextPage(int count) {
        int end = Math.min(allEntries.size(), renderedCount + count);
        for (int i = renderedCount; i < end; i++) {
            PreviewEntry e = allEntries.get(i);
            VBox cell = createDynamicCell(e);
            int col = i % 2;         // 0,1 반복
            int row = i / 2;         // 행 인덱스
            entriesGrid.add(cell, col, row);
        }
        renderedCount = end;
    }

    /** 프리뷰 셀 1개 생성 (날짜 + 내용 요약 + 클릭 시 모달) */
    private VBox createDynamicCell(PreviewEntry e) {
        VBox wrap = new VBox(5);
        wrap.setFillWidth(true);
        wrap.setMaxWidth(Double.MAX_VALUE);

        // 날짜
        Label date = new Label(e.date() != null ? e.date().format(DAY_FMT) : "");
        date.setStyle("-fx-font-size:14; -fx-text-fill:#4a4a4a; -fx-padding:0 0 6 4;");

        // 카드
        StackPane card = new StackPane();
        card.setPrefHeight(170);
        card.setStyle(
                "-fx-background-color:white;" +
                        "-fx-background-radius:16;" +
                        "-fx-effect:dropshadow(gaussian, rgba(0,0,0,0.12), 10, 0.2, 0, 2);" +
                        "-fx-padding:12;"
        );
        card.setCursor(Cursor.HAND);

        Rectangle clip = new Rectangle();
        clip.setArcWidth(16); clip.setArcHeight(16);
        card.layoutBoundsProperty().addListener((o, ov, nv) -> {
            clip.setWidth(nv.getWidth()); clip.setHeight(nv.getHeight());
        });
        card.setClip(clip);

        Label preview = new Label(tidy(e.text(), 140));
        preview.setWrapText(true);
        preview.setStyle("-fx-font-size:12; -fx-text-fill:#222;");
        card.widthProperty().addListener((o, ov, nv) -> preview.setMaxWidth(nv.doubleValue() - 20));
        preview.setMouseTransparent(true);

        card.getChildren().add(preview);

        card.setOnMouseClicked(ev -> {
            if (ev.getButton() == MouseButton.PRIMARY && ev.getClickCount() >= 1) {
                openViewerModalByData(e);
            }
        });

        wrap.getChildren().addAll(date, card);
        return wrap;
    }

    /** 데이터 기반 보기 전용 모달 */
    private void openViewerModalByData(PreviewEntry e) {
        LocalDate date = e.date();
        String title = nvl(e.title());
        String text  = nvl(e.text());

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

    /* =======================================================================
     * 스크롤 이벤트: 바닥 90% 이상 → 다음 페이지 자동 로드
     * ======================================================================= */
    private void hookInfiniteScrollOnce() {
        if (entriesScroll == null || scrollHooked) return;
        scrollHooked = true;

        // ▶ 바닥 98% 이상 도달 시 다음 페이지 로드
        entriesScroll.vvalueProperty().addListener((obs, ov, v) -> {
            if (selectedBuddyId == null || renderedCount >= allEntries.size()) return;
            if (v.doubleValue() >= 0.98) {
                renderNextPage(NEXT_PAGE);
            }
        });

        // ▶ 뷰포트 크기(윈도우 리사이즈 등) 바뀌면, 화면이 비면 자동으로 더 채움
        entriesScroll.viewportBoundsProperty().addListener((obs, ov, nv) -> {
            fillViewportIfShort();
        });

        // ▶ 그리드 높이가 바뀔 때도 한 번 더 체크(초기 렌더 직후 안정화용)
        entriesGrid.heightProperty().addListener((obs, ov, nv) -> {
            fillViewportIfShort();
        });
    }

    /* =======================================================================
     * 레이아웃/공통 유틸
     * ======================================================================= */

    /** GridPane: 2열(50/50), 행 제약은 없음(행은 동적으로 증가) */
    private void setupGridColumnsOnly() {
        entriesGrid.getColumnConstraints().clear();
        entriesGrid.getRowConstraints().clear();

        ColumnConstraints c1 = new ColumnConstraints();
        c1.setPercentWidth(50); c1.setHgrow(Priority.ALWAYS);

        ColumnConstraints c2 = new ColumnConstraints();
        c2.setPercentWidth(50); c2.setHgrow(Priority.ALWAYS);

        entriesGrid.getColumnConstraints().addAll(c1, c2);
    }

    /** 가로폭만 부모에 바인딩(세로는 ScrollPane이 담당) */
    private void bindGridWidthToParent() {
        if (entriesGrid.getParent() instanceof Region pr) {
            entriesGrid.prefWidthProperty().bind(pr.widthProperty());
        } else {
            entriesGrid.parentProperty().addListener((o, oldP, p) -> {
                if (p instanceof Region pr2) {
                    entriesGrid.prefWidthProperty().bind(pr2.widthProperty());
                }
            });
        }
    }

    /** 버튼 눌림시 크기 튐 방지(1회성) */
    private void freezeAllButtonSizesOnce(Scene scene) {
        javafx.application.Platform.runLater(() -> {
            Parent root = scene.getRoot();
            root.applyCss(); root.layout();
            for (Node n : root.lookupAll(".button")) {
                if (n instanceof Button b) {
                    double w = b.prefWidth(-1), h = b.prefHeight(-1);
                    b.setMinSize(w, h); b.setPrefSize(w, h); b.setMaxSize(w, h);
                }
            }
        });
    }

    /* =======================================================================
     * 작은 헬퍼들
     * ======================================================================= */

    private static String tidy(String s, int limit) {
        String one = nvl(s).replace("\r", " ").replace("\n", " ").trim();
        return one.length() > limit ? one.substring(0, limit) + "…" : one;
    }
    private static String nvl(String s) { return s == null ? "" : s; }

    private Long parseUserId(String buddyId) {
        try { return Long.parseLong(buddyId); } catch (Exception e) { return null; }
    }

    // 좌측 표시용 모델(닉네임/캐릭터가 Dao에서 오면 여기에 그대로 넣어주면 됨)
    private record Buddy(String id, String name, CharacterType ctype) {}

    // 우측 프리뷰 표시용 모델
    private record PreviewEntry(Long id, LocalDate date, String title, String text) {}
}

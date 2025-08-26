package com.share.dairy.controller;

import com.share.dairy.app.Router;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;

import javafx.geometry.Insets;

import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;

import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;

import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;

import javafx.stage.Modality;
import javafx.stage.Stage;

import java.time.LocalDate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import javafx.geometry.Insets;
import java.util.stream.Collectors;

/**
 * ===========================================================
 *  5-3 OUR DIARY 화면 (카드형) — 모달(공유 일기 생성) 연동 통합본
 * -----------------------------------------------------------
 *  - ESC 키: 허브(5-1) 복귀
 *  - 우하단 NEW(+): 모달 띄워 "제목 + 버디선택" 후 결과 처리
 *  - DB 연동 전: FAKE_DATA 스위치로 더미 카드 표시
 *
 *  필요 FXML/클래스
 *   - /fxml/diary/our_diary/create-share-diary-dialog.fxml
 *   - com.share.dairy.controller.CreateShareDiaryDialogController
 *
 *  module-info.java
 *   opens com.share.dairy.controller to javafx.fxml;
 * ===========================================================
 */
public class OurDiaryController {

    /* ================== FXML 바인딩 ================== */

    /** 카드들을 배치하는 컨테이너(FlowPane) — FXML에서 fx:id="cardsFlow" */
    @FXML private FlowPane cardsFlow;

    /* ================== 화면 상태/옵션 ================== */

    /** 디자인 확인용 더미 데이터 사용 여부 — DB 붙이면 false로 바꾸면 됨 */
    private static final boolean FAKE_DATA = true;

    /* ================== 라이프사이클 ================== */

    @FXML
public void initialize() {
    // 1) ESC → 허브 복귀
    cardsFlow.sceneProperty().addListener((obs, oldScene, scene) -> {
        if (scene != null) {
            scene.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
                if (e.getCode() == KeyCode.ESCAPE) 
                {
                    goHub();
                    e.consume();
                }
            });
        }
    });

    // 2) Our Diary 카드 영역 레이아웃/배경
    cardsFlow.setHgap(36);                // 가로 간격
    cardsFlow.setVgap(36);                // 세로 간격
    cardsFlow.setPadding(new Insets(26)); // 패딩
    cardsFlow.setStyle(
        "-fx-background-color: rgba(255,255,255,0.40); -fx-background-radius: 14;"
    );

    // 3) 초기 렌더링
    List<DiaryCardData> data = FAKE_DATA ? fakeCards() : fetchFromDB();
    renderCards(data);
}



    /* ================== 네비게이션 ================== */

    /** 홈(허브)으로 복귀 — 팀 라우팅 정책 유지 */
    @FXML public void goHub() {
        Router.go("DiaryHub");
    }

    /** 좌측 탭 이동: 지금은 최소 변경 — 필요 시 팀 라우팅으로 교체 */
    @FXML public void goMyDiary()    { Router.go("DiaryHub"); }    // TODO: 5-2 연결되면 교체
    @FXML public void goOurDiary()   { renderCards(FAKE_DATA ? fakeCards() : fetchFromDB()); }
    @FXML public void goBuddyDiary() { Router.go("DiaryHub"); }    // TODO: 5-4 연결되면 교체

    /* ================== NEW(+) → 모달 열기 ================== */

    /**
     * 우하단 NEW(+ ) 버튼 핸들러
     * - 공유 일기 생성 모달을 띄우고, START 시 결과를 받아 화면에 즉시 반영
     * - DB 붙으면 생성 서비스 호출 후 목록을 다시 로드(fetchFromDB)하면 됨
     */
    @FXML
public void onNew() {
    try {
        // 0) 경로 확인
        String fxml = "/fxml/diary/our_diary/create-share-diary-dialog.fxml";
        var url = OurDiaryController.class.getResource(fxml);
        System.out.println("[DEBUG] dialog fxml url = " + url);
        if (url == null) {
            new Alert(Alert.AlertType.ERROR, "FXML 파일을 못 찾았습니다: " + fxml).showAndWait();
            return; // 경로 문제 확정
        }

        // 1) 로드
        FXMLLoader loader = new FXMLLoader(url);
        Parent root = loader.load();

        // 2) 컨트롤러
        CreateShareDiaryDialogController ctrl = loader.getController();

        // 이하 동일…
        List<CreateShareDiaryDialogController.BuddyLite> buddies = List.of(
            new CreateShareDiaryDialogController.BuddyLite("kk","K.K"),
            new CreateShareDiaryDialogController.BuddyLite("naki","NaKi"),
            new CreateShareDiaryDialogController.BuddyLite("gd","Guide")
        );
        ctrl.setBuddies(buddies);

        Stage owner = (Stage) cardsFlow.getScene().getWindow();
        Stage dialog = new Stage();
        dialog.initOwner(owner);
        dialog.initModality(Modality.WINDOW_MODAL);
        dialog.setTitle("새 공유 일기장");
        dialog.setScene(new Scene(root));

        owner.getScene().getRoot().setOpacity(0.6);
        try {
            dialog.showAndWait();
        } finally {
            owner.getScene().getRoot().setOpacity(1.0);
        }

        ctrl.getResult().ifPresent(res -> {
            var idToName = buddies.stream()
                .collect(java.util.stream.Collectors.toMap(
                    CreateShareDiaryDialogController.BuddyLite::id,
                    CreateShareDiaryDialogController.BuddyLite::name));
            var names = res.buddyIds().stream().map(id -> idToName.getOrDefault(id, id)).toList();
            var list = new java.util.ArrayList<>(FAKE_DATA ? fakeCards() : fetchFromDB());
            list.add(new DiaryCardData(res.title(), names, java.time.LocalDate.now()));
            renderCards(list);
        });

    } catch (Exception e) {
        e.printStackTrace(); // 콘솔에 실제 예외 출력
        new Alert(Alert.AlertType.ERROR, "모달 오픈 실패: " + e.getClass().getSimpleName() + " - " + e.getMessage()).showAndWait();
    }
}


    /* ================== 카드 렌더링 ================== */

    /** 카드 목록을 FlowPane에 채워 넣기 */
    private void renderCards(List<DiaryCardData> list) {
        cardsFlow.getChildren().clear();
        for (DiaryCardData d : list) {
            cardsFlow.getChildren().add(buildCard(d));
        }
    }

// 개별 카드 UI 구성 (설계도 느낌)
private Node buildCard(DiaryCardData d) {
    VBox card = new VBox(8);
    card.setPadding(new Insets(16));
    card.setPrefWidth(240); // 카드 폭 고정
    // 흰 카드 + 둥근 모서리 + 부드러운 그림자
    card.setStyle(
        "-fx-background-color: white;" +
        "-fx-background-radius:18;" +
        "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.12), 12, 0, 0, 4);"
    );

    // 제목
    Label title = new Label(d.title);
    title.setStyle("-fx-font-size:16; -fx-font-weight:800; -fx-text-fill:#2d2150;");

    // 멤버 목록
    VBox membersBox = new VBox(6);
    for (String m : d.members) {
        Label row = new Label("👤 " + m);
        row.setStyle("-fx-font-size:13; -fx-text-fill:#2d2150;");
        membersBox.getChildren().add(row);
    }

    // 시작 날짜
    Label start = new Label("start " + d.startDate);
    start.setStyle("-fx-font-size:12; -fx-text-fill:#6b6b6b;");

    // 클릭 안내 (유지)
    card.setOnMouseClicked(e ->
        new Alert(Alert.AlertType.INFORMATION, d.title + " 열기(상세는 추후 연결)").show()
    );

    card.getChildren().addAll(title, membersBox, start);
    return card;
}

    /** 디자인 확인용 더미 카드 */
    private List<DiaryCardData> fakeCards() {
        List<DiaryCardData> list = new ArrayList<>();
        list.add(new DiaryCardData("TITLE 1",
                Arrays.asList("Member1", "Member2", "Member3"),
                LocalDate.now().minusDays(15)));
        list.add(new DiaryCardData("TITLE 2",
                Arrays.asList("Member1", "Member2"),
                LocalDate.now().minusDays(30)));
        list.add(new DiaryCardData("TITLE 3",
                Arrays.asList("Member1", "Member2"),
                LocalDate.now().minusDays(50)));
        return list;
    }

    /** TODO: DB에서 OUR DIARY 목록 조회 → 카드 데이터로 변환 */
    private List<DiaryCardData> fetchFromDB() {
        // 팀 DB 규약에 맞춰 구현 예정
        return new ArrayList<>();
    }

    /* ================== 내부 모델 ================== */

    /** 카드에 필요한 최소 정보만 묶은 내부 DTO */
    private static class DiaryCardData {
        final String title;
        final List<String> members;
        final LocalDate startDate;

        DiaryCardData(String title, List<String> members, LocalDate startDate) {
            this.title = title;
            this.members = members;
            this.startDate = startDate;
        }
    }
}
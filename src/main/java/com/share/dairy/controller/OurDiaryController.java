package com.share.dairy.controller;

import com.share.dairy.app.Router;
import com.share.dairy.auth.UserSession;
import com.share.dairy.controller.CreateShareDiaryDialogController;
import com.share.dairy.dao.friend.FriendshipDao;
import com.share.dairy.dao.sharedDiary.SharedDiaryDao;
import com.share.dairy.dao.sharedDiary.SharedDiaryMemberDao;
import com.share.dairy.dto.sharedDiary.SharedDiaryCreateRequest;
import com.share.dairy.service.friend.FriendshipService;
import com.share.dairy.service.sharedDiary.SharedDiaryService;

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
import java.util.List;

public class OurDiaryController {

    @FXML private FlowPane cardsFlow;

    // 서비스 직접 생성 (컨트롤러에서 Spring DI 미사용)
    private final SharedDiaryService sharedService =
            new SharedDiaryService(new SharedDiaryDao(), new SharedDiaryMemberDao());

    // 친구 조회 서비스 (DB: friendship + users)
    private final FriendshipService friendshipService =
            new FriendshipService(new FriendshipDao());

    private long me() { return UserSession.requireId(); }

    @FXML
    public void initialize() {
        // ESC → 허브
        cardsFlow.sceneProperty().addListener((obs, oldScene, scene) -> {
            if (scene != null) {
                scene.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
                    if (e.getCode() == KeyCode.ESCAPE) { goHub(); e.consume(); }
                });
            }
        });

        // 레이아웃
        cardsFlow.setHgap(36);
        cardsFlow.setVgap(36);
        cardsFlow.setPadding(new Insets(26));
        cardsFlow.setStyle("-fx-background-color: rgba(255,255,255,0.40); -fx-background-radius: 14;");

        // 초기 로드: 항상 DB에서
        renderCards(fetchFromDB());
    }

    @FXML public void goHub()        { Router.go("DiaryHub"); }
    @FXML public void goMyDiary()    { Router.go("DiaryHub"); }
    @FXML public void goOurDiary()   { renderCards(fetchFromDB()); }
    @FXML public void goBuddyDiary() { Router.go("DiaryHub"); }

    @FXML
    public void onNew() {
        try {
            String fxml = "/fxml/diary/our_diary/create-share-diary-dialog.fxml";
            var url = OurDiaryController.class.getResource(fxml);
            if (url == null) {
                new Alert(Alert.AlertType.ERROR, "FXML 파일을 못 찾았습니다: " + fxml).showAndWait();
                return;
            }

            FXMLLoader loader = new FXMLLoader(url);
            Parent root = loader.load();
            CreateShareDiaryDialogController ctrl = loader.getController();

            // DB에서 ACCEPTED 친구 목록 로드 → 모달에 주입
            List<CreateShareDiaryDialogController.BuddyLite> buddies;
            try {
                buddies = friendshipService.getAcceptedBuddies(me()).stream()
                        .map(b -> new CreateShareDiaryDialogController.BuddyLite(b.id(), b.name()))
                        .toList();
            } catch (Exception ex) {
                ex.printStackTrace();
                new Alert(Alert.AlertType.ERROR, "친구 목록 로드 실패: " + ex.getMessage()).showAndWait();
                return;
            }
            if (buddies.isEmpty()) {
                new Alert(Alert.AlertType.INFORMATION, "수락된 친구가 없습니다. 먼저 친구를 추가/수락해주세요.").showAndWait();
            }
            ctrl.setBuddies(buddies);

            // 모달 표시
            Stage owner = (Stage) cardsFlow.getScene().getWindow();
            Stage dialog = new Stage();
            dialog.initOwner(owner);
            dialog.initModality(Modality.WINDOW_MODAL);
            dialog.setTitle("새 공유 일기장");
            dialog.setScene(new javafx.scene.Scene(root));

            owner.getScene().getRoot().setOpacity(0.6);
            try { dialog.showAndWait(); } finally { owner.getScene().getRoot().setOpacity(1.0); }

            // 결과 처리: DB 저장 → 목록 리로드
            ctrl.getResult().ifPresent(res -> {
                try {
                    var req = new SharedDiaryCreateRequest();
                    req.setSharedDiaryTitle(res.title());
                    req.setOwnerId(me());

                    sharedService.createWithMembers(req, res.buddyIds()); // List<Long>
                    renderCards(fetchFromDB());
                } catch (Exception ex) {
                    ex.printStackTrace();
                    new Alert(Alert.AlertType.ERROR, "공유일기 생성 실패: " + ex.getMessage()).showAndWait();
                }
            });

        } catch (Exception e) {
            e.printStackTrace();
            new Alert(Alert.AlertType.ERROR,
                    "모달 오픈 실패: " + e.getClass().getSimpleName() + " - " + e.getMessage()).showAndWait();
        }
    }

    private void renderCards(List<DiaryCardData> list) {
        cardsFlow.getChildren().clear();
        for (DiaryCardData d : list) cardsFlow.getChildren().add(buildCard(d));
    }

    private Node buildCard(DiaryCardData d) {
        VBox card = new VBox(8);
        card.setPadding(new Insets(16));
        card.setPrefWidth(240);
        card.setStyle("-fx-background-color: white;-fx-background-radius:18;-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.12), 12, 0, 0, 4);");

        Label title = new Label(d.title);
        title.setStyle("-fx-font-size:16; -fx-font-weight:800; -fx-text-fill:#2d2150;");

        VBox membersBox = new VBox(6);
        for (String m : d.members) {
            Label row = new Label("👤 " + m);
            row.setStyle("-fx-font-size:13; -fx-text-fill:#2d2150;");
            membersBox.getChildren().add(row);
        }

        Label start = new Label("start " + d.startDate);
        start.setStyle("-fx-font-size:12; -fx-text-fill:#6b6b6b;");

        card.setOnMouseClicked(e -> openRoom(d.id, d.title));

        card.getChildren().addAll(title, membersBox, start);
        return card;
    }

    // ✅ 방 화면 열기
    private void openRoom(long sharedDiaryId, String title) {
        try {
            var url = OurDiaryController.class.getResource("/fxml/diary/our_diary/our-diary-room.fxml");
            if (url == null) { new Alert(Alert.AlertType.ERROR, "방 FXML 없음").showAndWait(); return; }

            FXMLLoader loader = new FXMLLoader(url);
            Parent root = loader.load();
            OurDiaryRoomController ctrl = loader.getController();
            ctrl.initRoom(sharedDiaryId, title); // 방 초기화

            Stage st = new Stage();
            st.initOwner((Stage) cardsFlow.getScene().getWindow());
            st.initModality(Modality.WINDOW_MODAL);
            st.setTitle("OUR DIARY · " + title);
            st.setScene(new Scene(root));
            st.show();
        } catch (Exception ex) {
            new Alert(Alert.AlertType.ERROR, "방 열기 실패: " + ex.getMessage()).showAndWait();
        }
    }

    private List<DiaryCardData> fetchFromDB() {
        try {
            return sharedService.getCards(me()).stream()
                    .map(c -> new DiaryCardData(c.id(), c.title(), c.members(), c.startDate()))
                    .toList();
        } catch (Exception e) {
            new Alert(Alert.AlertType.ERROR, "OUR DIARY 로드 실패: " + e.getMessage()).showAndWait();
            return List.of();
        }
    }
    // ✅ id 포함
    private static class DiaryCardData {
        final long id; final String title; final List<String> members; final LocalDate startDate;
        DiaryCardData(long id, String title, List<String> members, LocalDate startDate) {
            this.id=id; this.title=title; this.members=members; this.startDate=startDate;
        }
    }
}
package com.share.dairy.controller;

import javafx.css.PseudoClass;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.*;
import javafx.scene.control.Label;
import javafx.stage.Stage;

import javafx.event.ActionEvent;
import java.util.*;
import java.util.stream.Collectors;

/**
 * FriendListPanel 컨트롤러
 * - 헤더 홈 아이콘: 배경 없이 표시
 * - 중앙: GridPane(2열, 각 50%)에 카드 렌더링 → 항상 정돈된 2열
 * - 하단(컨텐츠 내부 우하단): Select/Delete
 */
public class FriendListPanelController {

    /* ===== FXML 바인딩 ===== */
    @FXML private GridPane grid;

    @FXML private Button btnSelect;
    @FXML private Button btnDelete;

    @FXML private Button btnHomeTop;
    @FXML private Button btnMyInfo;
    @FXML private Button btnAddFriends;
    @FXML private Button btnBuddyList;

    /* ===== 내부 상태 ===== */
    private final List<Friend> friends = new ArrayList<>();
    private final Set<FriendCard> selected = new HashSet<>();
    private static final PseudoClass PSEUDO_SELECTED = PseudoClass.getPseudoClass("selected");

    @FXML
    private void initialize() {
        seedSampleData();
        renderGrid();

        btnSelect.setOnAction(e -> handleSelect());
        btnDelete.setOnAction(e -> handleDelete());

        btnHomeTop.setOnAction(e -> navigateHome());
        btnMyInfo.setOnAction(e -> navigateMyInfo());
        btnBuddyList.setOnAction(e -> { /* 현재 화면 */ });
        btnSelect.getStyleClass().addAll("action-btn", "primary");
        btnDelete.getStyleClass().addAll("action-btn", "danger");
    }

    /** 데모 데이터 (실서비스에선 Service/DAO 대체) */
    private void seedSampleData() {
        String kk    = resource("/common_images/kk.png");
        String naki  = resource("/common_images/naki.png");
        String guide = resource("/common_images/guide.png");

        friends.addAll(List.of(
                new Friend("K.K", kk),
                new Friend("Naki", naki),
                new Friend("Guide", guide),
                new Friend("K.K", kk),
                new Friend("K.K", kk),
                new Friend("K.K", kk),
                new Friend("K.K", kk),
                new Friend("K.K", kk)
        ));
    }

    private String resource(String path) {
        try { return Objects.requireNonNull(getClass().getResource(path)).toExternalForm(); }
        catch (Exception e) { return null; }
    }

    /* ====== 2열 GridPane에 카드 렌더링 ====== */
    private void renderGrid() {
        grid.getChildren().clear();

        int col = 0, row = 0;
        for (Friend f : friends) {
            FriendCard card = new FriendCard(f);

            // GridPane 칼럼 폭에 맞춰 가로로 꽉 차도록
            card.setMaxWidth(Double.MAX_VALUE);
            GridPane.setHgrow(card, Priority.ALWAYS);

            grid.add(card, col, row);
            col++;
            if (col == 2) { col = 0; row++; }
        }
    }

    /* ===== Select / Delete ===== */
    private void handleSelect() {
        if (selected.isEmpty()) { alert("선택된 친구가 없습니다."); return; }
        String names = selected.stream()
                .map(c -> c.friend.name())
                .distinct()
                .collect(Collectors.joining(", "));
        alert("선택된 친구: " + names);
    }

    private void handleDelete() {
        if (selected.isEmpty()) { alert("삭제할 친구를 선택하세요."); return; }

        Set<Friend> removeSet = selected.stream().map(c -> c.friend).collect(Collectors.toSet());
        friends.removeIf(removeSet::contains);

        // 그리드에서도 제거
        grid.getChildren().removeIf(n -> n instanceof FriendCard fc && removeSet.contains(fc.friend));
        selected.clear();

        // 레이아웃 정리(갭 없이 다시 채우기)
        reflowGrid();
    }

    /** 삭제 후 공백 없이 다시 배치 */
    private void reflowGrid() {
        List<FriendCard> cards = new ArrayList<>();
        for (javafx.scene.Node n : new ArrayList<>(grid.getChildren())) {
            if (n instanceof FriendCard fc) {
                cards.add(fc);
            }
        }
        grid.getChildren().clear();

        int col = 0, row = 0;
        for (FriendCard c : cards) {
            GridPane.setHgrow(c, Priority.ALWAYS);
            grid.add(c, col, row);
            col++;
            if (col == 2) { col = 0; row++; }
        }
    }

    /* ===== 네비게이션 (연결 지점) ===== */
    private void navigateHome()      { /* TODO: 라우팅 연결 */ }
    private void navigateMyInfo()    { /* TODO: 라우팅 연결 */ }
    private void navigateAddFriends(){ /* TODO: 라우팅 연결 */ }

    private void alert(String msg) {
        Alert a = new Alert(AlertType.INFORMATION);
        a.setHeaderText(null);
        a.setContentText(msg);
        a.showAndWait();
    }

    @FXML private void goMyInfo()     { System.out.println("goMyInfo"); /* Router.go("MyInfo"); */ }
    @FXML
    private void goAddFriends(ActionEvent e) {
        try {
            Parent next = FXMLLoader.load(
                    getClass().getResource("/fxml/FriendList/AddFriendsPanel.fxml")
            );
            // 현재 씬의 루트 교체
            Stage stage = (Stage) ((Node) e.getSource()).getScene().getWindow();
            stage.getScene().setRoot(next);
        } catch (Exception ex) {
            ex.printStackTrace();
            alert("화면 전환 실패: " + ex.getMessage());
        }
    }
    @FXML private void goBuddyList()  { System.out.println("goBuddyList"); /* 현재 페이지라면 무시 */ }

    /* ===== 모델 ===== */
    public record Friend(String name, String avatarUrl) {}

    /* ===== 카드 뷰 ===== */
    private class FriendCard extends StackPane {
        private final Friend friend;
        private boolean selectedState = false;

        FriendCard(Friend friend) {
            this.friend = friend;

            // 배경
            Region bg = new Region();
            bg.getStyleClass().add("friend-card-bg");

            // 내용(HBox): 아바타 + 이름
            ImageView avatar = new ImageView();
            if (friend.avatarUrl() != null) {
                try { avatar.setImage(new Image(friend.avatarUrl(), true)); } catch (Exception ignored) {}
            }
            avatar.setFitWidth(48);
            avatar.setFitHeight(48);
            avatar.setPreserveRatio(true);

            Label name = new Label(friend.name());
            name.getStyleClass().add("friend-name");

            HBox content = new HBox(14, avatar, name);
            content.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
            content.setPadding(new Insets(12));

            getChildren().addAll(bg, content);
            getStyleClass().add("friend-card");

            setMinHeight(88);
            setCursor(Cursor.HAND);

            setOnMouseClicked(ev -> {
                if (ev.getButton() == MouseButton.PRIMARY) {
                    toggleSelected();
                }
            });
        }

        void toggleSelected() { setSelected(!selectedState); }
        boolean isSelected()  { return selectedState; }
        void setSelected(boolean value) {
            this.selectedState = value;
            pseudoClassStateChanged(PSEUDO_SELECTED, value);
            if (value) selected.add(this); else selected.remove(this);
        }
    }
}

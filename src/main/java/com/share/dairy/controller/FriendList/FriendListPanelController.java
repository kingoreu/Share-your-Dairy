package com.share.dairy.controller.FriendList;

import com.share.dairy.auth.UserSession;
import com.share.dairy.controller.OverlayChildController;
import com.share.dairy.dao.friend.FriendshipDao;
import com.share.dairy.model.friend.Friendship;
import com.share.dairy.util.DBConnection;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;

import java.io.InputStream;
import java.net.URL;
import java.util.*;


public class FriendListPanelController extends OverlayChildController {

    /* ===== FXML ===== */
    @FXML private GridPane grid;
    @FXML private Button btnSelect;
    @FXML private Button btnDelete;

    /* ===== DAO & 상태 ===== */
    private final FriendshipDao dao = new FriendshipDao();
    private final Set<Long> selected = new HashSet<>();
    private boolean selectMode = false;

    /* ===== 캐릭터 파일 매핑 ===== */
    private static final Map<String,String> CHARACTER_FILE = Map.ofEntries(
            Map.entry("RACCOON","raccoon.png"), Map.entry("DOG","dog.png"),
            Map.entry("CAT","cat.png"),         Map.entry("BEAR","bear.png"),
            Map.entry("DEER","deer.png"),       Map.entry("DUCK","duck.png"),
            Map.entry("HAMSTER","hamster.png"), Map.entry("RABBIT","rabbit.png"),
            Map.entry("WOLF","wolf.png"),       Map.entry("RICHARD","richard.png"),
            Map.entry("TAKO","tako.png"),       Map.entry("ZZUNI","zzuni.png")
    );

    /* =========================== init =========================== */
    @FXML
    public void initialize(URL url, ResourceBundle rb) {
        btnDelete.setDisable(true);
        loadFriends();
    }

    /* ======================= Navigation ========================= */
    @FXML private void goMyInfo()    { open("/fxml/FriendList/MyInfoPanel.fxml"); }
    @FXML private void goAddFriends(){ open("/fxml/FriendList/AddFriendsPanel.fxml"); }

    private void switchTo(String fxmlPath) {
        try {
            URL url = getClass().getResource(fxmlPath);
            if (url == null) throw new IllegalStateException("FXML not found: " + fxmlPath);
            Parent root = FXMLLoader.load(url);
            if (grid != null && grid.getScene() != null) grid.getScene().setRoot(root);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /* ===================== Load & Render ======================== */
    private void loadFriends() {
        grid.getChildren().clear();
        var me = UserSession.get();
        if (me == null) return;

        List<Friendship> list;
        try { list = dao.findFriendsFor(me.getUserId()); }
        catch (Exception e) { e.printStackTrace(); return; }

        int col = 0, row = 0;
        for (var f : list) {
            long other = (f.getUserId() == me.getUserId()) ? f.getFriendId() : f.getUserId();
            var ui = fetchUser(other);
            if (ui == null) continue;

            Node card = buildCard(ui.userId, ui.displayName(), ui.character);
            grid.add(card, col, row);
            GridPane.setMargin(card, new Insets(0, 0, 0, 0));

            col++;
            if (col == 2) { col = 0; row++; }
        }
    }

    private Node buildCard(long friendId, String name, String charType) {
        StackPane root = new StackPane();
        root.getStyleClass().add("friend-card");

        Region bg = new Region();
        bg.getStyleClass().add("friend-card-bg");
        bg.setMinHeight(88);
        bg.setPrefHeight(88);
        bg.setMaxHeight(88);

        ImageView avatar = new ImageView(loadChar(charType));
        avatar.setFitWidth(56);
        avatar.setFitHeight(56);
        avatar.setPreserveRatio(true);

        Label title = new Label(name);
        title.getStyleClass().add("friend-name");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox content = new HBox(12, avatar, title, spacer);
        content.setAlignment(Pos.CENTER_LEFT);
        content.setPadding(new Insets(12));

        root.getChildren().addAll(bg, content);

        // 선택 모드: 클릭으로 토글
        root.setOnMouseClicked(e -> {
            if (!selectMode) return;
            if (selected.remove(friendId)) {
                bg.setStyle(""); // 선택 해제
            } else {
                selected.add(friendId);
                bg.setStyle("-fx-border-color:#7b4e6b; -fx-border-width:2;"); // 선택 표시
            }
            btnDelete.setDisable(selected.isEmpty());
        });

        return root;
    }

    /* ================== Select / Delete ========================= */
    @FXML
    private void onSelect() {
        selectMode = !selectMode;
        selected.clear();
        btnDelete.setDisable(true);
        btnSelect.setText(selectMode ? "Cancel" : "Select");
        // 선택 표시 초기화
        grid.getChildren().forEach(n -> n.setStyle(""));
    }

    @FXML
    private void onDelete() {
        if (selected.isEmpty()) return;
        var me = UserSession.get(); if (me == null) return;

        var confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "선택한 친구를 삭제할까요? (" + selected.size() + "명)", ButtonType.OK, ButtonType.CANCEL);
        if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;

        try (var con = DBConnection.getConnection()) {
            con.setAutoCommit(false);
            for (long fid : selected) {
                dao.delete(con, me.getUserId(), fid);  // 양방향 삭제
            }
            con.commit();
        } catch (Exception e) {
            e.printStackTrace();
        }

        onSelect();     // 선택모드 해제
        loadFriends();  // 갱신
    }

    /* ======================== Helpers ========================== */
    private Image loadChar(String type) {
        String key = (type == null ? "" : type.trim().toUpperCase());
        String file = CHARACTER_FILE.getOrDefault(key, "raccoon.png");
        try (InputStream in = getClass().getResourceAsStream("/character/" + file)) {
            return in != null ? new Image(in) : null;
        } catch (Exception e) { return null; }
    }

    private record UserMini(long userId, String login, String nickname, String character) {
        String displayName() {
            return (nickname != null && !nickname.isBlank()) ? nickname : login;
        }
    }

    private UserMini fetchUser(long userId) {
        String sql = "SELECT user_id, login_id, nickname, character_type FROM users WHERE user_id=?";
        try (var con = DBConnection.getConnection();
             var ps = con.prepareStatement(sql)) {
            ps.setLong(1, userId);
            try (var rs = ps.executeQuery()) {
                if (rs.next())
                    return new UserMini(
                            rs.getLong("user_id"),
                            rs.getString("login_id"),
                            rs.getString("nickname"),
                            rs.getString("character_type")
                    );
            }
        } catch (Exception e) { e.printStackTrace(); }
        return null;
    }
}
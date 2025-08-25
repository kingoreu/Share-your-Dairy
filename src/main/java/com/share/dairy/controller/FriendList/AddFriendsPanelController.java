package com.share.dairy.controller.FriendList;

import com.share.dairy.auth.UserSession;
import com.share.dairy.controller.OverlayChildController;
import com.share.dairy.dao.friend.FriendshipDao;
import com.share.dairy.model.enums.CharacterType;
import com.share.dairy.model.enums.FriendshipStatus;
import com.share.dairy.model.friend.Friendship;
import com.share.dairy.util.DBConnection;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;

import java.io.InputStream;
import java.net.URL;
import java.sql.Connection;
import java.util.*;

public class AddFriendsPanelController extends OverlayChildController {

    // ===== FXML =====
    @FXML private ImageView imgCharacter;
    @FXML private TextField tfSearchId;
    @FXML private Label lblResult;
    @FXML private Button btnAdd;
    @FXML private ListView<PendingRow> lvPending;
    @FXML private StackPane card;
    // ===== 내부 상태 =====
    private final FriendshipDao friendshipDao = new FriendshipDao();
    private Long   foundUserId   = null;
    private String foundNickname = null;
    private CharacterType foundCharacter= null;


    /* =========================
     *           INIT
     * ========================= */
    @FXML
    public void initialize(URL url, ResourceBundle rb) {
        tfSearchId.setOnAction(e -> onSearch());
        btnAdd.setDisable(true);
        lblResult.setText("");
        setCharacterPreview(foundCharacter);
        imgCharacter.setPreserveRatio(true);
        imgCharacter.setSmooth(true);
        imgCharacter.setCache(true);
        // 카드 안쪽(패딩 제외) 크기에 맞춰서 자동 리사이즈
        imgCharacter.fitWidthProperty().bind(card.widthProperty().subtract(36));
        imgCharacter.fitHeightProperty().bind(card.heightProperty().subtract(36));
        lvPending.setCellFactory(list -> new PendingCell());
        reloadPending();
    }

    /* =========================
     *        NAVIGATION
     * ========================= */
    @FXML private void goMyInfo()    { open("/fxml/FriendList/MyInfoPanel.fxml"); }
    @FXML private void goBuddyList() { open("/fxml/FriendList/FriendListPanel.fxml"); }

    private void switchScene(String fxmlPath) {
        try {
            URL url = getClass().getResource(fxmlPath);
            if (url == null) throw new IllegalStateException("FXML not found: " + fxmlPath);
            Parent root = FXMLLoader.load(url);
            Node any = (tfSearchId != null) ? tfSearchId : imgCharacter;
            if (any != null && any.getScene() != null) any.getScene().setRoot(root);
        } catch (Exception e) {
            e.printStackTrace();
            toast("화면 전환 실패: " + e.getMessage());
        }
    }

    /* =========================
     *        SEARCH FLOW
     * ========================= */
    @FXML
    private void onSearch() {
        String q = Optional.ofNullable(tfSearchId.getText()).orElse("").trim();
        if (q.isEmpty()) { toast("ID 또는 닉네임을 입력하세요."); return; }

        var me = UserSession.get();
        if (me == null) { toast("로그인 세션이 없습니다."); return; }

        var u = findUserByLoginOrNickname(q);
        if (u == null) {
            foundUserId = null; foundNickname = null; foundCharacter = null;
            btnAdd.setDisable(true);
            lblResult.setText("검색 결과가 없습니다.");
            setCharacterPreview(CharacterType.ZZUNI);
            return;
        }
        if (u.userId == me.getUserId()) {
            foundUserId = null;
            btnAdd.setDisable(true);
            lblResult.setText("본인은 추가할 수 없어요.");
            setCharacterPreview(u.character);
            return;
        }

        foundUserId   = u.userId;
        foundNickname = (u.nickname != null && !u.nickname.isBlank()) ? u.nickname : u.loginId;
        foundCharacter= (u.character);
        setCharacterPreview(foundCharacter);

        // 현재 관계 상태 한 번에 확인
        RelationState state = getRelationState(me.getUserId(), foundUserId);
        switch (state) {
            case NONE -> { lblResult.setText("검색 결과: " + foundNickname); btnAdd.setDisable(false); }
            case SENT_BY_ME -> { lblResult.setText("이미 친구 요청을 보냈어요: " + foundNickname); btnAdd.setDisable(true); }
            case SENT_BY_OTHER -> { lblResult.setText("상대가 먼저 보낸 대기 요청이 있어요. 아래 목록에서 수락/거절하세요."); btnAdd.setDisable(true); }
            case ACCEPTED -> { lblResult.setText("이미 친구입니다: " + foundNickname); btnAdd.setDisable(true); }
        }
    }

    @FXML
    private void onAdd() {
        if (foundUserId == null) { toast("먼저 검색을 해 주세요."); return; }
        var me = UserSession.get(); if (me == null) { toast("로그인 세션이 없습니다."); return; }

        try (Connection con = DBConnection.getConnection()) {
            // 1) 선제 상태 확인 (양방향 1회)
            var either = friendshipDao.findEither(con, me.getUserId(), foundUserId);

            if (either.isPresent()) {
                Friendship f = either.get();
                if (f.getFriendshipStatus() == FriendshipStatus.ACCEPTED) {
                    lblResult.setText("이미 친구입니다: " + foundNickname);
                    btnAdd.setDisable(true); return;
                }
                if (f.getFriendshipStatus() == FriendshipStatus.PENDING) {
                    if (f.getUserId() == me.getUserId()) {
                        lblResult.setText("이미 친구 요청을 보냈어요: " + foundNickname);
                    } else {
                        lblResult.setText("상대가 먼저 보낸 대기 요청이 있어요. 아래 목록에서 수락/거절하세요.");
                    }
                    btnAdd.setDisable(true); return;
                }
            }

            // 2) 신규 요청(PENDING) — 역방향 중복은 uq_friend_pair 로 차단
            con.setAutoCommit(false);
            int changed = friendshipDao.upsertPending(con, me.getUserId(), foundUserId);
            con.commit();

            if (changed > 0) {
                lblResult.setText("친구 요청을 보냈어요: " + foundNickname);
                btnAdd.setDisable(true);
                reloadPending();
            } else {
                lblResult.setText("요청을 보낼 수 없습니다. 잠시 후 다시 시도해 주세요.");
            }
        } catch (java.sql.SQLIntegrityConstraintViolationException dup) {
            String msg = dup.getMessage();
            if (msg != null && msg.contains("uq_friend_pair")) {
                lblResult.setText("상대가 먼저 보낸 대기 요청이 있어요. 아래 목록에서 수락/거절하세요.");
            } else {
                lblResult.setText("이미 보낸 요청이 있거나 친구 상태입니다.");
            }
        } catch (Exception e) {
            e.printStackTrace();
            toast("요청 실패: " + e.getMessage());
        }
    }

    /* =========================
     *     PENDING LIST (수락/거절)
     * ========================= */
    private void reloadPending() {
        var me = UserSession.get(); if (me == null) return;
        try {
            var list = new ArrayList<PendingRow>();
            var pendings = friendshipDao.findPendingFor(me.getUserId()); // 받은 요청들
            for (var f : pendings) {
                long requester = f.getUserId();
                var ui = fetchUser(requester);
                if (ui == null) continue;
                list.add(new PendingRow(requester, ui.loginId, ui.nickname, ui.character));
            }
            lvPending.getItems().setAll(list);
        } catch (Exception e) {
            e.printStackTrace();
            toast("요청 목록을 불러오지 못했습니다: " + e.getMessage());
        }
    }

    private void accept(long requesterId) {
        var me = UserSession.get(); if (me == null) return;
        try (var con = DBConnection.getConnection()) {
            friendshipDao.respondToIncoming(con, requesterId, me.getUserId(), FriendshipStatus.ACCEPTED);
        } catch (Exception e) { e.printStackTrace(); toast("수락 실패: " + e.getMessage()); }
        reloadPending();
        if (Objects.equals(foundUserId, requesterId)) {
            lblResult.setText("이미 친구입니다: " + foundNickname);
            btnAdd.setDisable(true);
        }
    }

    private void reject(long requesterId) {
        var me = UserSession.get(); if (me == null) return;
        try (var con = DBConnection.getConnection()) {
            friendshipDao.respondToIncoming(con, requesterId, me.getUserId(), FriendshipStatus.REJECTED);
        } catch (Exception e) { e.printStackTrace(); toast("거절 실패: " + e.getMessage()); }
        reloadPending();
    }

    /* =========================
     *           UTIL
     * ========================= */
    private void setCharacterPreview(CharacterType type) {
        if (type == null) type = CharacterType.ZZUNI;
        // String file = CHARACTER_FILE.getOrDefault(normalize(type), "raccoon.png");
        try (InputStream in = getClass().getResourceAsStream(type.getImagePath())) {
            imgCharacter.setImage(in != null ? new Image(in) : null);
        } catch (Exception ignored) {}
    }
    private String normalize(String raw) { return raw == null ? "" : raw.trim().toUpperCase(); }
    private void toast(String s) { if (lblResult != null) lblResult.setText(s); }

    /* 관계 상태 판별: DAO의 findEither 1회로 처리 */
    private RelationState getRelationState(long me, long target) {
        try (var con = DBConnection.getConnection()) {
            var either = friendshipDao.findEither(con, me, target);
            if (either.isEmpty()) return RelationState.NONE;
            var f = either.get();
            if (f.getFriendshipStatus() == FriendshipStatus.ACCEPTED) return RelationState.ACCEPTED;
            if (f.getFriendshipStatus() == FriendshipStatus.PENDING) {
                return (f.getUserId() == me) ? RelationState.SENT_BY_ME : RelationState.SENT_BY_OTHER;
            }
            return RelationState.NONE;
        } catch (Exception e) { e.printStackTrace(); return RelationState.NONE; }
    }
    private enum RelationState { NONE, SENT_BY_ME, SENT_BY_OTHER, ACCEPTED }

    /* ===== 사용자 조회 도우미 ===== */
    private static class UserMini {
        long userId; String loginId; String nickname; CharacterType character;
        UserMini(long id, String lid, String nn, CharacterType ch) { userId=id; loginId=lid; nickname=nn; character=ch; }
    }
    private UserMini fetchUser(long userId) {
        String sql = "SELECT user_id, login_id, nickname, character_type FROM users WHERE user_id=?";
        try (var con = DBConnection.getConnection(); var ps = con.prepareStatement(sql)) {
            ps.setLong(1, userId);
            try (var rs = ps.executeQuery()) {
                if (rs.next()) return new UserMini(
                    rs.getLong("user_id"), rs.getString("login_id"),
                    rs.getString("nickname"), CharacterType.fromString(rs.getString("character_type"))
                );
            }
        } catch (Exception e) { e.printStackTrace(); }
        return null;
    }
    private UserMini findUserByLoginOrNickname(String q) {
        String sql = """
            SELECT user_id, login_id, nickname, character_type
              FROM users
             WHERE login_id = ? OR nickname = ?
             LIMIT 1
        """;
        try (var con = DBConnection.getConnection(); var ps = con.prepareStatement(sql)) {
            ps.setString(1, q); ps.setString(2, q);
            try (var rs = ps.executeQuery()) {
                if (rs.next()) return new UserMini(
                    rs.getLong("user_id"), rs.getString("login_id"),
                    rs.getString("nickname"), CharacterType.fromString(rs.getString("character_type"))
                );
            }
        } catch (Exception e) { e.printStackTrace(); }
        return null;
    }

    /* ===== ListView Cell: 수락/거절 버튼 포함 ===== */
    private class PendingCell extends ListCell<PendingRow> {
        private final ImageView icon = new ImageView();
        private final Label name = new Label();
        private final Button accept = new Button("수락");
        private final Button reject = new Button("거절");
        private final HBox root = new HBox(12, icon, name, new HBox(8, accept, reject));

        PendingCell() {
            icon.setFitWidth(42); icon.setFitHeight(42); icon.setPreserveRatio(true);
            name.setStyle("-fx-font-size:14px;-fx-font-weight:700;");
            HBox.setHgrow(name, javafx.scene.layout.Priority.ALWAYS);
            accept.getStyleClass().addAll("action-btn","primary");
            reject.getStyleClass().addAll("action-btn","danger");
            root.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        }
        @Override protected void updateItem(PendingRow it, boolean empty) {
            super.updateItem(it, empty);
            if (empty || it == null) { setGraphic(null); return; }
            // String file = CHARACTER_FILE.getOrDefault(it.character, "raccoon.png");
            try (InputStream in = getClass().getResourceAsStream(it.character.getImagePath())) {
                icon.setImage(in != null ? new Image(in) : null);
            } catch (Exception ignored) {}
            name.setText(it.nickname + " (" + it.loginId + ")");
            accept.setOnAction(e -> accept(it.userId));
            reject.setOnAction(e -> reject(it.userId));
            setGraphic(root);
        }
    }
    private record PendingRow(long userId, String loginId, String nickname, CharacterType character) {}

}

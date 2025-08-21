package com.share.dairy.controller.FriendList;

import com.share.dairy.controller.OverlayChildController;
import com.share.dairy.util.DBConnection;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

import java.io.InputStream;
import java.net.URL;
import java.sql.*;
import java.util.Optional;
import java.util.ResourceBundle;

/**
 * 친구 추가 화면 (OverlayChildController 상속)
 * - 홈 버튼: onAction="#goHome" (부모 공통)
 * - 사이드 탭 전환: host.openOverlay(...)
 */
public class AddFriendsPanelController extends OverlayChildController {

    // ===== FXML 바인딩 =====
    @FXML private TextField tfSearchId;
    @FXML private ImageView imgCharacter;
    @FXML private Label lblResult;
    @FXML private Button btnAdd;

    // 검색 결과 상태
    private Long   foundUserId   = null;
    private String foundNickname = null;
    private String foundCharType = null;

    // ===== 초기화 =====
    @FXML
    public void initialize(URL location, ResourceBundle resources) {
        if (tfSearchId != null) tfSearchId.setOnAction(e -> onSearch(null));
        if (btnAdd != null) btnAdd.setDisable(true);
        setCharacterPreviewByType(null);
        if (lblResult != null) lblResult.setText("");
    }

    // ===== 사이드 전환(오버레이 내) =====
    @FXML private void goMyInfo(ActionEvent e)     { if (host != null) host.openOverlay("/fxml/FriendList/MyInfoPanel.fxml"); }
    @FXML private void goBuddyList(ActionEvent e)  { if (host != null) host.openOverlay("/fxml/FriendList/FriendListPanel.fxml"); }

    // ===== 검색 =====
    @FXML
    private void onSearch(ActionEvent e) {
        String q = safeTrim(tfSearchId != null ? tfSearchId.getText() : "");
        foundUserId = null; foundNickname = null; foundCharType = null;
        if (btnAdd != null) btnAdd.setDisable(true);

        if (q.isEmpty()) {
            setMsg("검색할 ID(또는 닉네임)를 입력해 주세요.");
            setCharacterPreviewByType(null);
            return;
        }

        long me = getCurrentUserId();
        Optional<UserRow> row = findUserByLoginIdOrNickname(q);

        if (row.isEmpty()) {
            setMsg("일치하는 사용자를 찾지 못했어요.");
            setCharacterPreviewByType(null);
            return;
        }

        UserRow u = row.get();
        if (u.userId == me) {
            setMsg("본인은 친구로 추가할 수 없어요.");
            setCharacterPreviewByType(u.characterType);
            return;
        }

        FriendshipState state = checkFriendshipState(me, u.userId);
        switch (state) {
            case NONE -> {
                setMsg("찾았어요: " + u.nickname + " (추가 가능)");
                foundUserId   = u.userId;
                foundNickname = u.nickname;
                foundCharType = u.characterType;
                if (btnAdd != null) btnAdd.setDisable(false);
            }
            case PENDING_ME    -> setMsg("이미 친구 요청을 보냈어요. 상대의 수락을 기다려 주세요.");
            case PENDING_OTHER -> setMsg("상대가 보낸 요청이 있어요. 요청함/받음 화면에서 처리해 주세요.");
            case ACCEPTED      -> setMsg("이미 친구예요!");
        }

        setCharacterPreviewByType(foundCharType != null ? foundCharType : u.characterType);
    }

    // ===== 추가(친구 요청) =====
    @FXML
    private void onAdd(ActionEvent e) {
        if (foundUserId == null) {
            setMsg("먼저 검색을 해 주세요.");
            return;
        }
        long me = getCurrentUserId();
        if (me == foundUserId) {
            setMsg("본인은 추가할 수 없어요.");
            return;
        }

        FriendshipState state = checkFriendshipState(me, foundUserId);
        if (state != FriendshipState.NONE) {
            setMsg("이미 관계가 존재합니다. 상태: " + state);
            return;
        }

        boolean ok = insertFriendRequest(me, foundUserId);
        if (ok) {
            setMsg("친구 요청을 보냈어요: " + foundNickname);
            if (btnAdd != null) btnAdd.setDisable(true);
        } else {
            setMsg("요청 중 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.");
        }
    }

    // ====== DB 구현 ======

    /** 현재 로그인 유저 ID (세션 연동) */
    private long getCurrentUserId() {
        return 1L; // TODO: 실제 세션 값으로 교체
    }

    /** users 테이블에서 login_id 또는 nickname 으로 단건 검색 */
    private Optional<UserRow> findUserByLoginIdOrNickname(String q) {
        final String sql =
                "SELECT user_id, login_id, nickname, character_type " +
                "  FROM users " +
                " WHERE login_id = ? OR nickname = ? " +
                " LIMIT 1";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, q);
            ps.setString(2, q);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                UserRow u = new UserRow();
                u.userId        = rs.getLong("user_id");
                u.loginId       = rs.getString("login_id");
                u.nickname      = rs.getString("nickname");
                u.characterType = rs.getString("character_type");
                return Optional.of(u);
            }
        } catch (SQLException ex) {
            setMsg("검색 오류: " + ex.getMessage());
            return Optional.empty();
        }
    }

    /** 양방향을 고려해 최신 상태 1건을 조회 */
    private FriendshipState checkFriendshipState(long me, long target) {
        final String sql =
                "SELECT friendship_status, user_id, friend_id " +
                "  FROM friendship " +
                " WHERE (user_id = ? AND friend_id = ?) " +
                "    OR (user_id = ? AND friend_id = ?) " +
                " ORDER BY requested_at DESC " +
                " LIMIT 1";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, me);
            ps.setLong(2, target);
            ps.setLong(3, target);
            ps.setLong(4, me);

            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return FriendshipState.NONE;

                String status = rs.getString("friendship_status");
                long u = rs.getLong("user_id");
                long f = rs.getLong("friend_id");

                if ("ACCEPTED".equalsIgnoreCase(status)) return FriendshipState.ACCEPTED;
                if ("PENDING".equalsIgnoreCase(status)) {
                    if (u == me && f == target) return FriendshipState.PENDING_ME;
                    else                        return FriendshipState.PENDING_OTHER;
                }
                return FriendshipState.NONE;
            }
        } catch (SQLException ex) {
            setMsg("상태 확인 오류: " + ex.getMessage());
            return FriendshipState.NONE;
        }
    }

    /** 친구 요청 저장 (PENDING) */
    private boolean insertFriendRequest(long me, long target) {
        // 자기 자신 방지(앱 레벨)
        if (me == target) return false;

        final String sql =
                "INSERT INTO friendship (user_id, friend_id, friendship_status, requested_at) " +
                "VALUES (?, ?, 'PENDING', NOW())";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, me);
            ps.setLong(2, target);
            return ps.executeUpdate() == 1;
        } catch (SQLException ex) {
            setMsg("요청 저장 오류: " + ex.getMessage());
            return false;
        }
    }

    // ===== 뷰 유틸 =====
    private void setMsg(String msg) {
        if (lblResult != null) lblResult.setText(msg);
    }
    private static String safeTrim(String s) { return s == null ? "" : s.trim(); }

    private void setCharacterPreviewByType(String type) {
        String path;
        if ("A".equalsIgnoreCase(type))      path = "/characters/char_a.png";
        else if ("B".equalsIgnoreCase(type)) path = "/characters/char_b.png";
        else if ("C".equalsIgnoreCase(type)) path = "/characters/char_c.png";
        else                                 path = "/characters/character_default.png";

        try (InputStream in = getClass().getResourceAsStream(path)) {
            if (in != null) imgCharacter.setImage(new Image(in));
            else            imgCharacter.setImage(null);
        } catch (Exception ex) {
            imgCharacter.setImage(null);
        }
    }

    // ===== 내부 타입 =====
    private static class UserRow {
        long   userId;
        String loginId;
        String nickname;
        String characterType; // 'A'/'B'/'C'...
    }
    private enum FriendshipState { NONE, PENDING_ME, PENDING_OTHER, ACCEPTED }
}

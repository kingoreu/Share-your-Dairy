package com.share.dairy.controller;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

import java.io.InputStream;
import java.sql.*;
import java.util.Objects;
import java.util.Optional;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Node;
import java.net.URL;


/**
 * Add Friends 화면 컨트롤러 (DB 미연동 스텁 버전)
 * - UI 이벤트/흐름만 먼저 잡고, DB는 나중에 DatabaseManager 붙이면 됩니다.
 */
public class AddFriendsPanelController {

    // ===== FXML 바인딩 =====
    @FXML private TextField tfSearchId;
    @FXML private ImageView imgCharacter;
    @FXML private Label lblResult;
    @FXML private Button btnAdd;

    // 검색 결과 상태(ADD 버튼용)
    private Long   foundUserId   = null;
    private String foundNickname = null;
    private String foundCharType = null;

    // ===== 초기화 =====
    @FXML
    private void initialize() {
        // Enter 입력 시 검색 실행
        if (tfSearchId != null) {
            tfSearchId.setOnAction(e -> onSearch(null));
        }
        if (btnAdd != null) btnAdd.setDisable(true);
        setCharacterPreviewByType(null);
        if (lblResult != null) lblResult.setText("");
    }

    // ===== 라우팅(필요 시 구현) =====
    @FXML private void goHome(ActionEvent e) { /* AppRouter.navigate("home"); */ }
    @FXML private void goMyInfo(ActionEvent e) { /* AppRouter.navigate("myinfo"); */ }
    @FXML private void goBuddyList(ActionEvent e) {switchTo("/fxml/FriendList/FriendListPanel.fxml", (Node) e.getSource());}

    // ===== Search 버튼 핸들러 =====
    @FXML
    private void onSearch(ActionEvent e) {
        String q = safeTrim(tfSearchId != null ? tfSearchId.getText() : "");
        foundUserId = null;
        foundNickname = null;
        foundCharType = null;
        if (btnAdd != null) btnAdd.setDisable(true);

        if (q.isEmpty()) {
            setMsg("검색할 ID를 입력해 주세요.");
            setCharacterPreviewByType(null);
            return;
        }

        long me = getCurrentUserId();

        Optional<UserRow> row = findUserByLoginIdOrNickname(q);
        if (!row.isPresent()) {
            setMsg("일치하는 사용자를 찾지 못했어요.");
            setCharacterPreviewByType(null);
            return;
        }

        UserRow u = row.get();

        if (u.userId == me) {
            setMsg("본인은 친구로 추가할 수 없어요. 다른 ID를 검색해 주세요.");
            setCharacterPreviewByType(u.characterType);
            return;
        }

        // 친구 상태 확인
        FriendshipState state = checkFriendshipState(me, u.userId);
        switch (state) {
            case NONE:
                setMsg("찾았어요: " + u.nickname + "  (추가 가능)");
                foundUserId   = u.userId;
                foundNickname = u.nickname;
                foundCharType = u.characterType;
                if (btnAdd != null) btnAdd.setDisable(false);
                break;

            case PENDING_ME:
                setMsg("이미 친구 요청을 보냈어요. 상대방의 수락을 기다려 주세요.");
                break;

            case PENDING_OTHER:
                setMsg("상대방이 먼저 친구 요청을 보냈어요. 요청함/받음 화면에서 처리해 주세요.");
                break;

            case ACCEPTED:
                setMsg("이미 친구예요!");
                break;

            default:
                setMsg("상태를 확인할 수 없어요.");
        }

        // 캐릭터 미리보기
        if (foundCharType != null) setCharacterPreviewByType(foundCharType);
        else setCharacterPreviewByType(u.characterType);
    }

    // ===== ADD 버튼 핸들러 =====
    @FXML
    private void onAdd(ActionEvent e) {
        if (foundUserId == null) {
            setMsg("먼저 ID를 검색해 주세요.");
            return;
        }
        long me = getCurrentUserId();
        if (me == foundUserId) {
            setMsg("본인은 추가할 수 없어요.");
            return;
        }

        // 중복 요청 방지(더블클릭 등)
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
            setMsg("친구 요청 중 오류가 발생했어요. 잠시 후 다시 시도해 주세요.");
        }
    }

    // ===== 뷰 유틸 =====
    private void setMsg(String msg) {
        if (lblResult != null) lblResult.setText(msg);
    }

    // 캐릭터 타입별 미리보기
    private void setCharacterPreviewByType(String type) {
        String path;
        if ("A".equalsIgnoreCase(type))      path = "/characters/char_a.png";
        else if ("B".equalsIgnoreCase(type)) path = "/characters/char_b.png";
        else if ("C".equalsIgnoreCase(type)) path = "/characters/char_c.png";
        else                                 path = "/characters/character_default.png";

        try (InputStream in = getClass().getResourceAsStream(path)) {
            if (in != null) imgCharacter.setImage(new Image(in));
            else            imgCharacter.setImage(null); // 리소스 없으면 비움
        } catch (Exception ex) {
            imgCharacter.setImage(null);
        }
    }

    // ===== DB 스텁/예시 =====

    /** 로그인 유저 ID 가져오기 (실제 세션에서 교체) */
    private long getCurrentUserId() {
        return 1L; // TODO: 실제 로그인 세션 값으로 교체
    }

    /** 안전 trim */
    private static String safeTrim(String s) { return s == null ? "" : s.trim(); }

    /** DB 커넥션 (지금은 스텁: 실제 구현으로 교체하세요) */
    private Connection getConnection() throws SQLException {
        // TODO: DatabaseManager.getInstance().getConnection(); 로 교체
        // throw로 남겨두면 컴파일은 되지만 실행 시 이 경로로 못 들어오게(위에서 미리 반환 처리).
        throw new SQLException("DatabaseManager 연결을 구현하세요.");
    }

    /** 사용자 검색: login_id 또는 nickname 으로 단건 조회 */
    private Optional<UserRow> findUserByLoginIdOrNickname(String q) {
        // === 스텁 동작: DB 없이 가짜 한 건 리턴 (컴파일/데모용) ===
        // DB 붙이면 아래 JDBC 코드 주석 해제하고 스텁 제거하세요.
        if ("K.K".equalsIgnoreCase(q)) {
            UserRow u = new UserRow();
            u.userId = 2L;
            u.loginId = "K.K";
            u.nickname = "K.K";
            u.characterType = "A";
            return Optional.of(u);
        }
        return Optional.empty();

        /* ===== JDBC 예시 =====
        final String sql =
                "SELECT user_id, login_id, nickname, character_type " +
                "  FROM users WHERE login_id = ? OR nickname = ? LIMIT 1";
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, q);
            ps.setString(2, q);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    UserRow u = new UserRow();
                    u.userId = rs.getLong("user_id");
                    u.loginId = rs.getString("login_id");
                    u.nickname = rs.getString("nickname");
                    u.characterType = rs.getString("character_type");
                    return Optional.of(u);
                }
            }
        } catch (SQLException ex) {
            setMsg("검색 오류: " + ex.getMessage());
        }
        return Optional.empty();
        */
    }

    /** 친구 상태 조회 */
    private FriendshipState checkFriendshipState(long me, long target) {
        // === 스텁: 항상 NONE ===
        return FriendshipState.NONE;

        /* ===== JDBC 예시 =====
        final String sql =
            "SELECT friendship_status, user_id, friend_id " +
            "  FROM friendship " +
            " WHERE (user_id = ? AND friend_id = ?) OR (user_id = ? AND friend_id = ?) " +
            " ORDER BY requested_at DESC LIMIT 1";
        try (Connection con = getConnection();
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
                    else return FriendshipState.PENDING_OTHER;
                }
                return FriendshipState.NONE;
            }
        } catch (SQLException ex) {
            setMsg("상태 확인 오류: " + ex.getMessage());
            return FriendshipState.NONE;
        }
        */
    }

    /** 친구 요청 저장 */
    private boolean insertFriendRequest(long me, long target) {
        // === 스텁: 성공 처리로 가정 ===
        return true;

        /* ===== JDBC 예시 =====
        final String sql =
            "INSERT INTO friendship (user_id, friend_id, friendship_status, requested_at) " +
            "VALUES (?, ?, 'PENDING', NOW())";
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, me);
            ps.setLong(2, target);
            return ps.executeUpdate() == 1;
        } catch (SQLException ex) {
            setMsg("친구 요청 저장 오류: " + ex.getMessage());
            return false;
        }
        */
    }

    // ===== 내부 타입 =====
    private static class UserRow {
        long   userId;
        String loginId;
        String nickname;
        String characterType; // 'A' / 'B' / 'C' ...
    }

    private enum FriendshipState {
        NONE, PENDING_ME, PENDING_OTHER, ACCEPTED
    }

    // AddFriendsPanelController 내부에 메서드 추가
    private void switchTo(String fxmlPath, Node trigger) {
        try {
            URL url = getClass().getResource(fxmlPath);
            if (url == null) { setMsg("화면 파일을 찾지 못했습니다: " + fxmlPath); return; }
            Parent root = FXMLLoader.load(url);
            trigger.getScene().setRoot(root);
        } catch (Exception ex) {
            ex.printStackTrace();
            setMsg("화면 전환 실패: " + ex.getMessage());
        }
    }
}

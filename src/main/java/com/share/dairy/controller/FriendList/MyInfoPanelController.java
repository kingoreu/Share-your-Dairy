package com.share.dairy.controller.FriendList;

import com.share.dairy.auth.UserSession;
import com.share.dairy.controller.OverlayChildController;
import com.share.dairy.model.enums.CharacterType;
import com.share.dairy.util.DBConnection;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;            // ESC 단축키
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.layout.BorderPane;       // FXML 루트
import javafx.scene.layout.StackPane;
import java.util.Map;
import java.util.ResourceBundle;
import java.io.InputStream;
import java.net.URL;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.LinkedHashMap;

public class MyInfoPanelController extends OverlayChildController {

    // ===== FXML 바인딩 =====
    @FXML private StackPane card;
    @FXML private ImageView imgCharacter;

    @FXML private TextField tfId;
    @FXML private PasswordField pfPassword;
    @FXML private TextField tfEmail;
    @FXML private TextField tfNickname;
    @FXML private ComboBox<CharacterType> cbCharacter;

    @FXML private Button btnEdit;
    @FXML private Label  lblHint;

    private boolean editing = false;

//    private static final Map<String, String> CHARACTER_FILE = Map.ofEntries(
//        Map.entry("RACCOON", "raccoon.png"),
//        Map.entry("DOG",     "dog.png"),
//        Map.entry("CAT",     "cat.png"),
//        Map.entry("BEAR",    "bear.png"),
//        Map.entry("DEER",    "deer.png"),
//        Map.entry("DUCK",    "duck.png"),
//        Map.entry("HAMSTER", "hamster.png"),
//        Map.entry("RABBIT",  "rabbit.png"),
//        Map.entry("WOLF",    "wolf.png"),
//        Map.entry("RICHARD", "richard.png"),
//        Map.entry("TAKO",    "tako.png"),
//        Map.entry("ZZUNI",   "zzuni.png")
//    );

    // ===== 초기화 =====
    @Override
    public void initialize(URL url, ResourceBundle rb) {
        // 이미지가 카드 폭에 맞게 줄어들도록
        if (card != null && imgCharacter != null) {
            imgCharacter.fitWidthProperty().bind(card.widthProperty().subtract(36));
            imgCharacter.fitHeightProperty().bind(card.heightProperty().subtract(36));
            imgCharacter.setSmooth(true);
            imgCharacter.setPreserveRatio(true);
            imgCharacter.setCache(true);
        }

        // 콤보박스 채우기 + 변경 시 프리뷰 반영
        cbCharacter.getItems().setAll(CharacterType.values());
//        cbCharacter.getSelectionModel().selectedItemProperty().addListener(
//            (obs, o, n) -> setCharacterPreviewByType(n)
//        );
        cbCharacter.getSelectionModel().selectedItemProperty().addListener(
                (obs, o, n) -> setCharacterPreviewByType(n)
        );

        // 사용자 정보 로드(세션 → 필요 시 DB 보완)
        loadMyInfoFromSessionOrDb();

        setEditing(false);
    }

    // ===== 네비게이션 =====
    @FXML private void goAddFriends(){ open("/fxml/FriendList/AddFriendsPanel.fxml"); }
    @FXML private void goBuddyList() { open("/fxml/FriendList/FriendListPanel.fxml"); }

    // ===== Edit/Save 토글 =====
    @FXML
    private void onEditToggle(ActionEvent e) {
        if (!editing) {
            setEditing(true);
            lblHint.setText("수정 후 Save를 눌러 저장하세요.");
            btnEdit.setText("Save");
        } else {
            if (!validateInputs()) return;

            // TODO: 서버 API 호출로 바꾸면 더 좋음 (/api/users/{id} PUT)
            boolean ok = updateMyInfoToDb();

        if (ok) {
            setEditing(false);
            btnEdit.setText("Edit");
            lblHint.setText("저장 완료!");

                // 세션 갱신
                var s = UserSession.get();
                var sel = cbCharacter.getSelectionModel().getSelectedItem();
                // if (s != null && sel != null) s.setCharacterType(normalize(sel));
                // 수정
                // enum 타입으로 변경 **
                if (s != null && sel != null) {
                    s.setCharacterType(sel); // 문자열 → Enum
                }

                setCharacterPreviewByType(sel);
            } else {
                lblHint.setText("저장 중 오류가 발생했어요. 잠시 후 다시 시도해 주세요.");
            }
        }
    }

    private boolean updateMyInfoToDb() {
        var s = UserSession.get();
        if (s == null) {
            hint("로그인 세션이 없습니다. 다시 로그인해 주세요.");
            return false;
        }

        String email     = tfEmail.getText() == null ? "" : tfEmail.getText().trim();
        String nickname  = tfNickname.getText() == null ? "" : tfNickname.getText().trim();
        //String character = normalize(cbCharacter.getSelectionModel().getSelectedItem());
        CharacterType type = cbCharacter.getSelectionModel().getSelectedItem();

        String sql = "UPDATE users SET user_email=?, nickname=?, character_type=?, user_updated_at=NOW() WHERE user_id=?";

        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setString(1, email);
            ps.setString(2, nickname);
            ps.setString(3, type != null ? type.name() : null);
            // ps.setString(3, character);
            ps.setLong(4, s.getUserId());

            int updated = ps.executeUpdate();
            if (updated == 1) {
                // 세션에도 반영
                s.setEmail(email);
                s.setNickname(nickname);
                s.setCharacterType(type);
                return true;
            } else {
                hint("업데이트된 행이 없습니다. user_id를 확인하세요.");
                return false;
            }
        } catch (Exception e) {
            e.printStackTrace();
            hint("DB 업데이트 실패: " + e.getMessage());
            return false;
        }
    }

    private void setEditing(boolean on) {
        this.editing = on;
        tfId.setEditable(false);
        pfPassword.setEditable(on);
        tfEmail.setEditable(on);
        tfNickname.setEditable(on);
        cbCharacter.setDisable(!on);
    }

    // ===== 데이터 로딩 =====
    private void loadMyInfoFromSessionOrDb() {
        String loginId = null, email = null, nickname = null;
        CharacterType character = null;

        var s = UserSession.get();
        if (s != null) {
            loginId  = s.getLoginId();
            email    = s.getEmail();
            nickname = s.getNickname();
            character = s.getCharacterType();
            // enum 타입으로 변경 **
//            character = (s.getCharacterType() != null)
//                    ? s.getCharacterType().name()
//                    : null;

            // 필요 시 DB로 보완 조회(예: null 값이 있을 때)
            if (email == null || nickname == null || character == null) {
                var info = fetchUserInfoById(s.getUserId());
                if (info != null) {
                    if (email == null)    email    = info.email;
                    if (nickname == null) nickname = info.nickname;
                    if (character == null)character= info.character;
                }
            }
        } else {
            // 개발 편의: 첫 사용자 조회(운영에선 제거)
            var info = fetchFirstUser();
            if (info != null) {
                loginId  = info.loginId;
                email    = info.email;
                nickname = info.nickname;
                character= info.character;
            }
        }

        // UI 바인딩
        tfId.setText(loginId != null ? loginId : "");
        tfEmail.setText(email != null ? email : "");
        tfNickname.setText(nickname != null ? nickname : "");

        // 정규화하는 부분 같은데 필요 없음
//        String norm = (character != null) ? normalize(character) : "RACCOON";
//        if (!cbCharacter.getItems().contains(norm)) cbCharacter.getItems().add(norm);
//        cbCharacter.getSelectionModel().select(norm);
//        setCharacterPreviewByType(norm);

        if (character != null) {
            cbCharacter.getSelectionModel().select(character);
            setCharacterPreviewByType(character);
        } else {
            cbCharacter.getSelectionModel().clearSelection();
            setCharacterPreviewByType(CharacterType.ZZUNI);
        }
    }

    private record UserInfo(String loginId, String email, String nickname, CharacterType character){}

    private UserInfo fetchUserInfoById(long userId) {
        String sql = "SELECT login_id, user_email, nickname, character_type FROM users WHERE user_id = ?";
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new UserInfo(
                        rs.getString("login_id"),
                        rs.getString("user_email"),
                        rs.getString("nickname"),
                        CharacterType.fromString(rs.getString("character_type"))
                    );
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            hint("사용자 조회 실패: " + e.getMessage());
        }
        return null;
    }

    private UserInfo fetchFirstUser() {
        String sql = "SELECT login_id, user_email, nickname, character_type FROM users ORDER BY user_id LIMIT 1";
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return new UserInfo(
                    rs.getString("login_id"),
                    rs.getString("user_email"),
                    rs.getString("nickname"),
                    CharacterType.fromString(rs.getString("character_type"))
                );
            }
        } catch (Exception e) {
            e.printStackTrace();
            hint("사용자 조회 실패: " + e.getMessage());
        }
        return null;
    }

    private boolean validateInputs() {
        if (tfEmail.getText() == null || !tfEmail.getText().contains("@")) {
            hint("이메일 형식이 올바르지 않아요.");
            return false;
        }
        if (pfPassword.getText() == null || pfPassword.getText().length() < 4) {
            hint("비밀번호는 4자 이상으로 입력하세요.");
            return false;
        }
        return true;
    }

    // ===== 캐릭터 미리보기 =====
    private void setCharacterPreviewByType(CharacterType type) {
        // String key  = normalize(type);
        // String file = CHARACTER_FILE.getOrDefault(key, "raccoon.png"); // 기본값
        if (type == null) type = CharacterType.ZZUNI;
        String path = type.getImagePath(); // ⚠ 리소스 경로 확인
        try (InputStream in = getClass().getResourceAsStream(path)) {
            imgCharacter.setImage(in != null ? new Image(in) : null);
        } catch (Exception ignored) {}
    }

    private String normalize(String raw) {
        return raw == null ? "" : raw.trim().toUpperCase();
    }

    private void hint(String s){ if (lblHint != null) lblHint.setText(s); }
}

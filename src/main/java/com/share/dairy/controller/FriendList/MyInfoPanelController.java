package com.share.dairy.controller.FriendList;

import com.share.dairy.auth.UserSession;
import com.share.dairy.controller.OverlayChildController;
import com.share.dairy.util.DBConnection;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import java.util.Map;
import java.util.ResourceBundle;
import java.io.InputStream;
import java.net.URL;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class MyInfoPanelController extends OverlayChildController{

    // ===== FXML 바인딩 =====
    @FXML private StackPane card;
    @FXML private ImageView imgCharacter;

    @FXML private TextField tfId;
    @FXML private PasswordField pfPassword;
    @FXML private TextField tfEmail;
    @FXML private TextField tfNickname;
    @FXML private ComboBox<String> cbCharacter;

    @FXML private Button btnEdit;
    @FXML private Label  lblHint;

    private boolean editing = false;

    private static final Map<String, String> CHARACTER_FILE = Map.ofEntries(
    Map.entry("RACCOON", "raccoon.png"),
    Map.entry("DOG",     "dog.png"),
    Map.entry("CAT",     "cat.png"),
    Map.entry("BEAR",    "bear.png"),
    Map.entry("DEER",    "deer.png"),
    Map.entry("DUCK",    "duck.png"),
    Map.entry("HAMSTER", "hamster.png"),
    Map.entry("RABBIT",  "rabbit.png"),
    Map.entry("WOLF",    "wolf.png"),
    Map.entry("RICHARD", "richard.png"),
    Map.entry("TAKO",    "tako.png"),
    Map.entry("ZZUNI",   "zzuni.png")
    );

    // ===== 초기화 =====
    @FXML
     public void initialize(URL url, ResourceBundle rb) {

        
        // 이미지가 카드 폭에 맞게 줄어들도록
        if (card != null && imgCharacter != null) {
            imgCharacter.fitWidthProperty().bind(card.widthProperty().subtract(36));
            imgCharacter.fitHeightProperty().bind(card.heightProperty().subtract(36));
            imgCharacter.setSmooth(true);
            imgCharacter.setPreserveRatio(true);
            imgCharacter.setCache(true);
        }

        // 캐릭터 옵션
        cbCharacter.getItems().setAll(CHARACTER_FILE.keySet());
        cbCharacter.getSelectionModel().selectedItemProperty().addListener(
            (obs, o, n) -> setCharacterPreviewByType(n)
        );

        // 로그인한 사용자 정보 로딩(세션 → 필요 시 DB 보완)
        loadMyInfoFromSessionOrDb();

        setEditing(false);
    }

    // ===== 화면 전환 (좌측 네비) =====
    @FXML private void goHome(ActionEvent e)      { /* Router.navigate("home"); */ }
    @FXML private void goAddFriends(ActionEvent e){ switchTo("/fxml/FriendList/AddFriendsPanel.fxml", (Node)e.getSource()); }
    @FXML private void goBuddyList(ActionEvent e) { switchTo("/fxml/FriendList/FriendListPanel.fxml", (Node)e.getSource()); }

    private void switchTo(String fxmlPath, Node trigger){
        try{
            URL url = getClass().getResource(fxmlPath);
            if (url == null) { hint("화면 파일을 찾을 수 없어요: " + fxmlPath); return; }
            Parent root = FXMLLoader.load(url);
            trigger.getScene().setRoot(root);
        }catch(Exception ex){
            ex.printStackTrace();
            hint("화면 전환 실패: " + ex.getMessage());
        }
    }

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
            boolean ok = updateMyInfoLocalStub();

            if (ok) {
                setEditing(false);
                btnEdit.setText("Edit");
                lblHint.setText("저장 완료!");

                // 세션 갱신
                var s = UserSession.get();
                var sel = cbCharacter.getSelectionModel().getSelectedItem();
                if (s != null && sel != null) s.setCharacterType(normalize(sel));
                setCharacterPreviewByType(sel);
            } else {
                lblHint.setText("저장 중 오류가 발생했어요. 잠시 후 다시 시도해 주세요.");
            }
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
        String loginId = null, email = null, nickname = null, character = null;

        var s = UserSession.get();
        if (s != null) {
            loginId = s.getLoginId();
            email = s.getEmail();
            nickname = s.getNickname();
            character = s.getCharacterType();

            // 필요 시 DB로 보완 조회(예: null 값이 있을 때)
            if (email == null || nickname == null) {
                var info = fetchUserInfoById(s.getUserId());
                if (info != null) {
                    if (email == null) email = info.email;
                    if (nickname == null) nickname = info.nickname;
                    if (character == null) character = info.character;
                }
            }
        } else {
            // 개발 편의: 첫 사용자 조회(운영에선 제거)
            var info = fetchFirstUser();
            if (info != null) {
                loginId = info.loginId;
                email = info.email;
                nickname = info.nickname;
                character = info.character;
            }
        }

        // UI 바인딩
        tfId.setText(loginId != null ? loginId : "");
        tfEmail.setText(email != null ? email : "");
        tfNickname.setText(nickname != null ? nickname : "");
        String norm = (character != null) ? normalize(character) : "RACCOON";
        if (!cbCharacter.getItems().contains(norm)) cbCharacter.getItems().add(norm);
        cbCharacter.getSelectionModel().select(norm);
        setCharacterPreviewByType(norm);
    }

    private record UserInfo(String loginId, String email, String nickname, String character){}

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
                        rs.getString("character_type")
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
                    rs.getString("character_type")
                );
            }
        } catch (Exception e) {
            e.printStackTrace();
            hint("사용자 조회 실패: " + e.getMessage());
        }
        return null;
    }

    // ===== 저장 (지금은 스텁: 필요 시 서버 PUT으로 교체) =====
    private boolean updateMyInfoLocalStub() {
        // 서버 API로 바꾸려면 /api/users/{UserSession.get().userId} 로 PUT 보내세요.
        return true;
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
    private void setCharacterPreviewByType(String type) {
        String key = normalize(type);
        String file = CHARACTER_FILE.getOrDefault(key, "raccoon.png"); // 기본값
        String path = "/character/" + file; // ⚠ 폴더명은 단수(character)
        try (InputStream in = getClass().getResourceAsStream(path)) {
            imgCharacter.setImage(in != null ? new Image(in) : null);
        } catch (Exception ignored) {}
    }

    private String normalize(String raw) {
        return raw == null ? "" : raw.trim().toUpperCase();
    }

    private void hint(String s){ if (lblHint != null) lblHint.setText(s); }
}

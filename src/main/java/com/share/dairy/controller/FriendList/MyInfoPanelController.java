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


/**
 * 내 정보 화면 컨트롤러 (Overlay 버전)
 *
 * - 메인/ESC 처리: OverlayChildController의 goHome() 사용 → host.closeOverlay()
 *   * FXML 홈 버튼 onAction="#goHome"으로 부모 메서드 직접 호출
 *   * ESC 단축키도 this::goHome 등록
 *
 * - 화면 전환:
 *   * AddFriends / BuddyList → openOverlay("...") (부모에서 제공)
 *   * 더 이상 수동 FXMLLoader/Scene 교체 없음
 *
 * - 레이아웃 안정화:
 *   * ImageView 크기를 캐릭터 카드(StackPane) 크기에 바인딩
 *   * 이미지 생성 시 요청 크기(800x800)로 생성
 */
public class MyInfoPanelController extends OverlayChildController {

    /* ===== FXML 바인딩 ===== */
    @FXML private BorderPane root;          // FXML 루트(BorderPane) — ESC 등록용
    @FXML private StackPane  card;          // 캐릭터 미리보기 카드(크기 상한은 FXML에서 지정)
    @FXML private ImageView  imgCharacter;  // 캐릭터 이미지뷰

    @FXML private TextField      tfId;
    @FXML private PasswordField  pfPassword;
    @FXML private TextField      tfEmail;
    @FXML private TextField      tfNickname;
    @FXML private ComboBox<String> cbCharacter;

    @FXML private Button btnEdit;
    @FXML private Label  lblHint;

    /* ===== 내부 상태 ===== */
    private boolean editing = false;

    /** 캐릭터 타입 → 파일명 매핑 (리소스 경로: /character/*.png) */
    private static final Map<String, String> CHARACTER_FILE = new LinkedHashMap<>() {{
        put("RACCOON", "raccoon.png");
        put("DOG",     "dog.png");
        put("CAT",     "cat.png");
        put("BEAR",    "bear.png");
        put("DEER",    "deer.png");
        put("DUCK",    "duck.png");
        put("HAMSTER", "hamster.png");
        put("RABBIT",  "rabbit.png");
        put("WOLF",    "wolf.png");
        put("RICHARD", "richard.png");
        put("TAKO",    "tako.png");
        put("ZZUNI",   "zzuni.png");
    }};

    /* ===== 초기화 ===== */
    @FXML
    public void initialize(URL url, ResourceBundle rb) {

        // [레이아웃] 이미지가 카드 안에서만 커지도록 — 패딩(18px*2)을 고려해 36px 감산
        if (card != null && imgCharacter != null) {
            imgCharacter.fitWidthProperty().bind(card.widthProperty().subtract(36));
            imgCharacter.fitHeightProperty().bind(card.heightProperty().subtract(36));
            imgCharacter.setPreserveRatio(true);
            imgCharacter.setSmooth(true);
            imgCharacter.setCache(true);
        }

        // 콤보박스 채우기 + 변경 시 프리뷰 반영
        cbCharacter.getItems().setAll(CHARACTER_FILE.keySet());
        cbCharacter.getSelectionModel().selectedItemProperty()
                .addListener((obs, o, n) -> setCharacterPreviewByType(n));

        // 사용자 정보 로드(세션 → 필요 시 DB 보완)
        loadMyInfoFromSessionOrDb();

        // 읽기 전용 시작
        setEditing(false);

        // [ESC 단축키 등록]
        //  - Scene이 붙는 순간에만 getAccelerators 사용 가능하므로 listener에서 등록
        //  - Runnable 시그니처에 맞춰 this::goHome 사용(부모의 goHome() 호출)
        root.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {
                newScene.getAccelerators().put(
                        new KeyCodeCombination(KeyCode.ESCAPE),
                        this::goHome  // OverlayChildController.goHome() → host.closeOverlay()
                );
            }
        });
    }

    /* ===== 사이드바/헤더 네비게이션 ===== */

    // 홈 아이콘: FXML에서 onAction="#goHome"으로 부모 메서드를 바로 호출하면 된다.
    // (메서드 재정의/오버로드 불필요. 필요하면 아래처럼 위임만 해도 됨)
    // @FXML private void goHomeClick(ActionEvent e) { goHome(); }

    @FXML
    private void goAddFriends(ActionEvent e) {
        // 오버레이 내부 이동: 부모가 제공하는 openOverlay 사용
        openOverlay("/fxml/FriendList/AddFriendsPanel.fxml");
    }

    @FXML
    private void goBuddyList(ActionEvent e) {
        openOverlay("/fxml/FriendList/FriendListPanel.fxml");
    }

    /* ===== Edit/Save 토글 ===== */

    @FXML
    private void onEditToggle(ActionEvent e) {
        if (!editing) {
            // Edit → 입력 가능
            setEditing(true);
            lblHint.setText("수정 후 Save를 눌러 저장하세요.");
            btnEdit.setText("Save");
            return;
        }

        // Save → 검증 후 저장
        if (!validateInputs()) return;

        // (지금은 스텁) 실제 운영은 서버 API(/api/users/{id} PUT)로 교체 권장
        boolean ok = updateMyInfoLocalStub();

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
                    s.setCharacterType(CharacterType.fromString(sel)); // 문자열 → Enum
                }

                setCharacterPreviewByType(sel);
            } else {
                lblHint.setText("저장 중 오류가 발생했어요. 잠시 후 다시 시도해 주세요.");
            }
        }
    }

    /** 입력 가능/불가 상태 전환 */
    private void setEditing(boolean on) {
        this.editing = on;
        tfId.setEditable(false);            // ID는 고정
        pfPassword.setEditable(on);
        tfEmail.setEditable(on);
        tfNickname.setEditable(on);
        cbCharacter.setDisable(!on);
    }

    /* ===== 데이터 로딩 ===== */

    /**
     * 1) UserSession
     * 2) 필요 시 DB 보완
     * 3) [개발 편의] 세션 없으면 첫 사용자 1명 로드(운영에선 제거 권장)
     */
    private void loadMyInfoFromSessionOrDb() {
        String loginId = null, email = null, nickname = null, character = null;

        var s = UserSession.get();
        if (s != null) {
            loginId  = s.getLoginId();
            email    = s.getEmail();
            nickname = s.getNickname();
            // character = s.getCharacterType();
            // enum 타입으로 변경 **
            character = (s.getCharacterType() != null) ? s.getCharacterType().name() : null;

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

        tfId.setText(loginId != null ? loginId : "");
        tfEmail.setText(email != null ? email : "");
        tfNickname.setText(nickname != null ? nickname : "");

        String norm = (character != null) ? normalize(character) : "RACCOON";
        if (!cbCharacter.getItems().contains(norm)) cbCharacter.getItems().add(norm);
        cbCharacter.getSelectionModel().select(norm);

        setCharacterPreviewByType(norm);
    }

    /** 단순 DTO */
    private record UserInfo(String loginId, String email, String nickname, String character){}

    /** user_id로 사용자 1명 조회 */
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

    /** 첫 사용자 1명 조회(개발 편의) */
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

    /* ===== 저장(현재는 스텁) ===== */

    /** 실제 운영은 REST API(/api/users/{id} PUT) 호출로 교체 권장 */
    private boolean updateMyInfoLocalStub() {
        // TODO: 서버 호출로 교체
        return true;
    }

    /** 기본 유효성 검사 */
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

    /* ===== 캐릭터 미리보기 ===== */

    /**
     * type → 파일명 → 리소스 스트림 → Image 생성
     * - 레이아웃 안정화를 위해 요청 크기(800x800) 지정
     */
    private void setCharacterPreviewByType(String type) {
        String key  = normalize(type);
        String file = CHARACTER_FILE.getOrDefault(key, "raccoon.png"); // 기본값
        String path = "/character/" + file;                             // 리소스 경로(단수 폴더명!)
        try (InputStream in = getClass().getResourceAsStream(path)) {
            if (in != null) {
                Image img = new Image(in, 800, 800, true, true);
                imgCharacter.setImage(img);
            } else {
                imgCharacter.setImage(null);
                hint("이미지를 찾을 수 없어요: " + path);
            }
        } catch (Exception e) {
            e.printStackTrace();
            hint("이미지 로딩 실패: " + e.getMessage());
        }
    }

    /* ===== 유틸 ===== */

    /** 대소문자·양끝 공백 정규화 */
    private String normalize(String raw) { return raw == null ? "" : raw.trim().toUpperCase(); }

    /** 하단 안내 메시지 */
    private void hint(String s){ if (lblHint != null) lblHint.setText(s); }
}

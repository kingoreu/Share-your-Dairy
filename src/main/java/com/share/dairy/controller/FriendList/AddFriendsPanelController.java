package com.share.dairy.controller.FriendList;

import com.share.dairy.controller.OverlayChildController;

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

import javafx.scene.input.KeyCode;            // ESC 단축키
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.layout.BorderPane;        // 루트 컨테이너
import javafx.scene.layout.StackPane;

/**
 * Add Friends 화면 컨트롤러 (Overlay 흐름 적용)
 * - 홈/ESC: goHome() → host.closeOverlay(), 단 host==null이면 메인 FXML로 안전 fallback
 * - 탭 이동: openOverlay("...") 사용
 * - 시그니처: Initializable 요구에 맞춰 initialize(URL, ResourceBundle) 구현
 */
public class AddFriendsPanelController extends OverlayChildController {

    /* ===== FXML 바인딩 ===== */
    @FXML private BorderPane root;        // ESC 등록/오버레이 제어
    @FXML private StackPane  card;        // 캐릭터 카드(상한은 FXML에서 지정)
    @FXML private ImageView  imgCharacter;

    @FXML private TextField tfSearchId;
    @FXML private Label     lblResult;
    @FXML private Button    btnAdd;
    @FXML private Button    btnSearch;

    /* ===== 검색 결과 상태(ADD 버튼 제어용) ===== */
    private Long   foundUserId   = null;
    private String foundNickname = null;
    private String foundCharType = null;

    /* ===== Initializable 시그니처로 구현 (중요!) ===== */
    @Override
    public void initialize(URL url, ResourceBundle rb) {
        // [레이아웃] 이미지가 카드 안에서만 커지도록 바인딩 (패딩 18px * 2 = 36px 고려)
        if (card != null && imgCharacter != null) {
            imgCharacter.fitWidthProperty().bind(card.widthProperty().subtract(36));
            imgCharacter.fitHeightProperty().bind(card.heightProperty().subtract(36));
            imgCharacter.setPreserveRatio(true);
            imgCharacter.setSmooth(true);
            imgCharacter.setCache(true);
        }

        // Enter 키로도 검색되게
        if (tfSearchId != null) tfSearchId.setOnAction(e -> onSearch(null));

        // 초기 상태
        if (btnAdd != null) btnAdd.setDisable(true);
        setCharacterPreviewByType(null); // 기본 이미지
        if (lblResult != null) lblResult.setText("");

        // [ESC 단축키 등록]
        root.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {
                newScene.getAccelerators().put(
                        new KeyCodeCombination(KeyCode.ESCAPE),
                        this::goHome // 아래 override된 goHome 사용(미주입 시 fallback 수행)
                );
            }
        });
    }

    /* ===== Overlay 주입 완료 알림(Optional 디버깅용) ===== */
    @Override
    protected void onHostReady() {
        // System.out.println("Overlay host injected? " + (host != null));
    }

    /* ===== 홈/ESC 동작: host 없으면 안전하게 메인으로 fallback ===== */
    @FXML @Override
    protected void goHome() {
        if (host != null) {
            host.closeOverlay();
            return;
        }
        // ★ host 미주입 환경(직접 FXMLLoader로 열었을 때 등)에 대비한 안전 fallback
        fallbackToMain();
    }

    /** host 없는 경우를 위한 안전한 메인 전환 */
    private void fallbackToMain() {
        try {
            // 프로젝트에 맞게 경로 조정
            javafx.fxml.FXMLLoader loader =
                    new javafx.fxml.FXMLLoader(getClass().getResource("/fxml/Main.fxml"));
            javafx.scene.Parent next = loader.load();
            root.getScene().setRoot(next);
        } catch (Exception ex) {
            ex.printStackTrace();
            setMsg("메인 화면 전환 실패: " + ex.getMessage());
        }
    }

    /* ===== 좌측 사이드바 네비게이션 ===== */

    @FXML
    private void goMyInfo(ActionEvent e) {
        if (host != null) openOverlay("/fxml/FriendList/MyInfoPanel.fxml");
        else setMsg("호스트 미주입 상태입니다. (Main에서 openOverlay로 열어주세요)");
    }

    @FXML
    private void goBuddyList(ActionEvent e) {
        if (host != null) openOverlay("/fxml/FriendList/FriendListPanel.fxml");
        else setMsg("호스트 미주입 상태입니다. (Main에서 openOverlay로 열어주세요)");
    }

    /* ===== Search 버튼 ===== */
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

        FriendshipState state = checkFriendshipState(me, u.userId);
        switch (state) {
            case NONE -> {
                setMsg("찾았어요: " + u.nickname + "  (추가 가능)");
                foundUserId   = u.userId;
                foundNickname = u.nickname;
                foundCharType = u.characterType;
                if (btnAdd != null) btnAdd.setDisable(false);
            }
            case PENDING_ME    -> setMsg("이미 친구 요청을 보냈어요. 상대방의 수락을 기다려 주세요.");
            case PENDING_OTHER -> setMsg("상대방이 먼저 친구 요청을 보냈어요. 요청함/받음 화면에서 처리해 주세요.");
            case ACCEPTED      -> setMsg("이미 친구예요!");
            default            -> setMsg("상태를 확인할 수 없어요.");
        }

        // 캐릭터 미리보기
        if (foundCharType != null) setCharacterPreviewByType(foundCharType);
        else setCharacterPreviewByType(u.characterType);
    }

    /* ===== ADD 버튼 ===== */
    @FXML
    private void onAdd(ActionEvent e) {
        if (foundUserId == null) { setMsg("먼저 ID를 검색해 주세요."); return; }

        long me = getCurrentUserId();
        if (me == foundUserId) { setMsg("본인은 추가할 수 없어요."); return; }

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

    /* ===== 뷰 유틸 ===== */

    private void setMsg(String msg) {
        if (lblResult != null) lblResult.setText(msg);
    }

    private static String safeTrim(String s) { return s == null ? "" : s.trim(); }

    /**
     * 캐릭터 타입별 미리보기 (데모 매핑)
     * - A/B/C → /characters/char_*.png
     * - 기본 → /characters/character_default.png
     * - 초대형 원본 대비 Image(800x800)로 생성
     */
    private void setCharacterPreviewByType(String type) {
        String path;
        if ("A".equalsIgnoreCase(type))      path = "/characters/char_a.png";
        else if ("B".equalsIgnoreCase(type)) path = "/characters/char_b.png";
        else if ("C".equalsIgnoreCase(type)) path = "/characters/char_c.png";
        else                                 path = "/characters/character_default.png";

        try (InputStream in = getClass().getResourceAsStream(path)) {
            if (in != null) {
                Image img = new Image(in, 800, 800, true, true);
                imgCharacter.setImage(img);
            } else {
                imgCharacter.setImage(null);
            }
        } catch (Exception ex) {
            imgCharacter.setImage(null);
        }
    }

    /* ===== DB 스텁/예시 ===== */

    private long getCurrentUserId() { return 1L; } // TODO: UserSession.get().getUserId() 등으로 교체

    private Connection getConnection() throws SQLException {
        // TODO: DatabaseManager.getInstance().getConnection();
        throw new SQLException("DatabaseManager 연결을 구현하세요.");
    }

    private Optional<UserRow> findUserByLoginIdOrNickname(String q) {
        // 데모: "K.K"만 매칭
        if ("K.K".equalsIgnoreCase(q)) {
            UserRow u = new UserRow();
            u.userId = 2L;
            u.loginId = "K.K";
            u.nickname = "K.K";
            u.characterType = "A";
            return Optional.of(u);
        }
        return Optional.empty();
    }

    private FriendshipState checkFriendshipState(long me, long target) {
        // 데모: 항상 NONE
        return FriendshipState.NONE;
    }

    private boolean insertFriendRequest(long me, long target) {
        // 데모: 성공 가정
        return true;
    }

    /* ===== 내부 타입 ===== */

    private static class UserRow {
        long   userId;
        String loginId;
        String nickname;
        String characterType; // 'A' / 'B' / 'C' ...
    }

    private enum FriendshipState {
        NONE, PENDING_ME, PENDING_OTHER, ACCEPTED
    }
}

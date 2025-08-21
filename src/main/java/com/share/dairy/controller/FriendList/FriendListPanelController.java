package com.share.dairy.controller.FriendList;

import javafx.css.PseudoClass;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.Cursor;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.*;
import javafx.scene.control.Label;

import javafx.event.ActionEvent;

import java.util.*;
import java.util.stream.Collectors;

import com.share.dairy.controller.OverlayChildController;

/**
 * FriendListPanel 컨트롤러 (Overlay 흐름 적용)
 * ---------------------------------------------------------
 * - 홈 아이콘/ESC : OverlayChildController.goHome() → host.closeOverlay() 호출
 *                   (창을 닫는게 아니라 "오버레이"만 닫고 메인으로 복귀)
 * - AddFriends/MyInfo : openOverlay("...") 사용 (부모가 제공하는 API)
 *   → 더 이상 FXMLLoader/Stage/Scene 직접 제어하지 않음 (안정/일관성 ↑)
 *
 * - 중앙 컨텐츠 :
 *   GridPane(2열, 각 50%)에 카드 렌더링 → 항상 2열 정돈
 * - 하단(컨텐츠 내부 우하단) :
 *   Select/Delete 버튼 유지
 *
 * 주의:
 * - FXML 루트(BorderPane)에 fx:id="root"가 반드시 있어야 ESC 등록이 동작
 * - 현재 페이지(Buddy List) 버튼은 비활성/무시
 */
public class FriendListPanelController extends OverlayChildController {

    /* ===== FXML 바인딩 ===== */
    @FXML private BorderPane root;   // ⬅ ESC 단축키 등록/오버레이 제어에 사용
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

    // CSS : .friend-card:selected 같은 스타일 변경에 사용
    private static final PseudoClass PSEUDO_SELECTED = PseudoClass.getPseudoClass("selected");

    @FXML
    public void initialize(java.net.URL url, java.util.ResourceBundle rb) {
        // 데모 데이터 구성 (실서비스에선 Service/DAO 호출로 교체)
        seedSampleData();

        // 2열 그리드 렌더링
        renderGrid();

        // 버튼 핸들러
        btnSelect.setOnAction(e -> handleSelect());
        btnDelete.setOnAction(e -> handleDelete());

        // 홈 아이콘 → 부모의 goHome() (오버레이 닫기 = 메인 복귀)
        btnHomeTop.setOnAction(e -> goHome());

        // 사이드바 : Add Friends / My Info 는 오버레이 전환
        btnAddFriends.setOnAction(this::goAddFriends);
        btnMyInfo.setOnAction(this::goMyInfo);

        // 현재 위치(Buddy List)는 무시
        btnBuddyList.setOnAction(e -> { /* 현재 페이지 */ });

        // 버튼 공통 스타일 클래스(선택)
        btnSelect.getStyleClass().addAll("action-btn", "primary");
        btnDelete.getStyleClass().addAll("action-btn", "danger");

        // ===== ESC 단축키 등록 (Scene이 붙은 뒤 가능) =====
        root.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {
                newScene.getAccelerators().put(
                        new KeyCodeCombination(KeyCode.ESCAPE),
                        this::goHome // OverlayChildController.goHome() → host.closeOverlay()
                );
                // (원하면 이벤트 필터 방식도 가능)
                // newScene.addEventFilter(KeyEvent.KEY_PRESSED, ev -> {
                //     if (ev.getCode() == KeyCode.ESCAPE) { ev.consume(); goHome(); }
                // });
            }
        });
    }

    /* =========================
     * 오버레이 내 네비게이션
     * ========================= */

    /** 좌측 사이드바 - Add Friends로 전환 (오버레이 교체) */
    @FXML
    private void goAddFriends(ActionEvent e) {
        openOverlay("/fxml/FriendList/AddFriendsPanel.fxml");
    }

    /** 좌측 사이드바 - My Info로 전환 (오버레이 교체) */
    @FXML
    private void goMyInfo(ActionEvent e) {
        openOverlay("/fxml/FriendList/MyInfoPanel.fxml");
    }

    /* =========================
     * 데이터/그리드 렌더링
     * ========================= */

    /** 데모 데이터 (서비스 연동 전까지 임시) */
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

    /** 클래스패스 리소스 → URL 문자열 (ImageView 등에서 바로 사용) */
    private String resource(String path) {
        try { return Objects.requireNonNull(getClass().getResource(path)).toExternalForm(); }
        catch (Exception e) { return null; } // 없는 경우 null 허용(아바타 없이 이름만 표시)
    }

    /** 2열 GridPane에 카드 렌더링 */
    private void renderGrid() {
        grid.getChildren().clear();

        int col = 0, row = 0;
        for (Friend f : friends) {
            FriendCard card = new FriendCard(f);

            // 그리드 칼럼 폭에 맞춰 가로로 꽉 차도록
            card.setMaxWidth(Double.MAX_VALUE);
            GridPane.setHgrow(card, Priority.ALWAYS);

            grid.add(card, col, row);

            // 2열 고정
            col++;
            if (col == 2) { col = 0; row++; }
        }
    }

    /* =========================
     * Select / Delete 동작
     * ========================= */

    /** 선택된 카드들을 안내 */
    private void handleSelect() {
        if (selected.isEmpty()) { alert("선택된 친구가 없습니다."); return; }
        String names = selected.stream()
                .map(c -> c.friend.name())
                .distinct()
                .collect(Collectors.joining(", "));
        alert("선택된 친구: " + names);
    }

    /** 선택 삭제 후, 그리드를 재배치(빈칸 없이) */
    private void handleDelete() {
        if (selected.isEmpty()) { alert("삭제할 친구를 선택하세요."); return; }

        Set<Friend> removeSet = selected.stream().map(c -> c.friend).collect(Collectors.toSet());
        friends.removeIf(removeSet::contains);

        // 그리드에서 제거
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

    /* =========================
     * 유틸/도우미
     * ========================= */

    private void alert(String msg) {
        Alert a = new Alert(AlertType.INFORMATION);
        a.setHeaderText(null);
        a.setContentText(msg);
        a.showAndWait();
    }

    /* =========================
     * 모델 & 카드 뷰
     * ========================= */

    /** 간단한 모델 (이름/아바타URL) */
    public record Friend(String name, String avatarUrl) {}

    /**
     * 친구 카드 UI
     * - 배경 Region + (아바타, 이름) HBox
     * - 클릭 시 선택 토글 (PseudoClass로 스타일 변경)
     */
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

            // 클릭으로 선택 토글
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
            pseudoClassStateChanged(PSEUDO_SELECTED, value); // CSS : .friend-card:selected 등
            if (value) selected.add(this); else selected.remove(this);
        }
    }
}

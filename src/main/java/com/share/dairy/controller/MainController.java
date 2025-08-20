package com.share.dairy.controller;

import javafx.animation.FadeTransition;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Node;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

public class MainController {

    /* ===================== Overlay Host Interfaces (중첩 타입) ===================== */
    public interface OverlayHost {
        void closeOverlay();
        void openOverlay(String fxmlPath);
    }
    public interface NeedsOverlayHost {
        void setOverlayHost(OverlayHost host);
    }

    /* ===================== FXML Bindings ===================== */
    @FXML private Pane contentPane;

    @FXML private Rectangle wardrobeHotspot;
    @FXML private Rectangle windowHotspot;
    @FXML private Rectangle laptopHotspot;
    @FXML private Rectangle bookshelfHotspot;
    @FXML private Rectangle radioHotspot;

    @FXML private ImageView characterImg;

    /* ===================== Overlay Host Impl ===================== */
    private final OverlayHost overlayHost = new OverlayHost() {
        @Override public void closeOverlay() { closeContent(); }
        @Override public void openOverlay(String fxmlPath) { loadView(fxmlPath); }
    };

    /* ===================== Initialize ===================== */
    @FXML
    public void initialize() {
        // Overlay 레이어 초기 상태
        contentPane.setVisible(false);
        contentPane.setManaged(false);
        contentPane.setPickOnBounds(true);                   // 뒤 클릭 차단
        contentPane.setStyle("-fx-background-color: transparent;");
        contentPane.toBack();

        // Z-Order 정리
        wardrobeHotspot.toFront();
        windowHotspot.toFront();
        laptopHotspot.toFront();
        bookshelfHotspot.toFront();
        radioHotspot.toFront();
        characterImg.toFront();
        setOverlayVisible(true);

        // ESC로 닫기 (scene 준비 후 1회 등록)
        contentPane.sceneProperty().addListener((obs, oldScene, scene) -> {
            if (scene != null) {
                scene.setOnKeyPressed(e -> {
                    if (e.getCode() == KeyCode.ESCAPE) closeContent();
                });
            }
        });
    }

    /* ===================== Hotspot Click Handlers ===================== */
    @FXML private void onWardrobeClicked(MouseEvent e)   { /* TODO: 옷장 화면 */ }
    @FXML private void onWindowClicked(MouseEvent e)     { loadView("/fxml/moodGraph/mood-graph-view.fxml"); }
    @FXML private void onLaptopClicked(MouseEvent e)     { loadView("/fxml/diary/diary_hub/diary-hub-shell.fxml"); }
    @FXML private void onBookshelfClicked(MouseEvent e)  { /* TODO: 책장 화면 */ }
    @FXML private void onRadioClicked(MouseEvent e)      { loadView("/fxml/calendar/calendar.fxml"); }
    @FXML private void onCharacterClicked(MouseEvent e)  { loadView("/fxml/FriendList/MyInfoPanel.fxml"); }
//    @FXML private void onLaptopClicked(MouseEvent e) {
//        Platform.runLater(() -> Router.go("DiaryHub"));
//    }
    // @FXML private void onLaptopClicked(MouseEvent e)     { loadView("/fxml/diary/my_diary/my-diary-view.fxml"); }
    // @FXML private void onRadioClicked(MouseEvent e)      { loadView("/fxml/diary/our_diary/home-view.fxml"); }
    // @FXML private void onCharacterClicked(MouseEvent e)  { loadView("/fxml/userInfo/settings-view.fxml"); }

    /* ===================== Overlay Loader ===================== */
    private void loadView(String fxmlPath) {
        try {
            var url = getClass().getResource(fxmlPath);
            if (url == null) throw new IllegalStateException("FXML not found: " + fxmlPath);

            // 컨트롤러 인스턴스를 얻기 위해 loader 사용
            FXMLLoader loader = new FXMLLoader(url);
            Parent view = loader.load();

            // 자식 컨트롤러가 OverlayHost를 필요로 하면 주입
            Object controller = loader.getController();
            if (controller instanceof NeedsOverlayHost needsHost) {
                needsHost.setOverlayHost(overlayHost);
            }

            // 컨텐츠 교체 및 표출
            contentPane.getChildren().setAll(view);
            contentPane.setVisible(true);
            contentPane.setManaged(true);
            contentPane.setMouseTransparent(false); // ★ 보여줄 땐 입력 받기
            contentPane.toFront();

            // 뒤 배경 살짝 어둡게
            contentPane.setStyle("-fx-background-color: rgba(0,0,0,0.24);");

            // 로드된 뷰가 컨테이너를 가득 채우도록 바인딩
            if (view instanceof javafx.scene.layout.Region r) {
                r.prefWidthProperty().bind(contentPane.widthProperty());
                r.prefHeightProperty().bind(contentPane.heightProperty());
            }

            // 기본(핫스팟/캐릭터) 오버레이 숨김
            setOverlayVisible(false);

            // 페이드 인
            animateFadeIn(view);

        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    /* ===================== Overlay Visibility ===================== */
    private void setOverlayVisible(boolean v) {
        wardrobeHotspot.setVisible(v);
        windowHotspot.setVisible(v);
        laptopHotspot.setVisible(v);
        bookshelfHotspot.setVisible(v);
        radioHotspot.setVisible(v);
        characterImg.setVisible(v);
        if (v) {
            // 오버레이가 보일 땐 항상 앞에 오도록
            wardrobeHotspot.toFront();
            windowHotspot.toFront();
            laptopHotspot.toFront();
            bookshelfHotspot.toFront();
            radioHotspot.toFront();
            characterImg.toFront();
        }
    }

    /* ===================== Close Overlay ===================== */
    private void closeContent() {
        contentPane.getChildren().clear();
        contentPane.setVisible(false);
        contentPane.setManaged(false);
        contentPane.setStyle("-fx-background-color: transparent;");
        setOverlayVisible(true);
    }

    /* ===================== Animation ===================== */
    private void animateFadeIn(Node view) {
        FadeTransition ft = new FadeTransition(Duration.millis(180), view);
        ft.setFromValue(0);
        ft.setToValue(1);
        ft.play();
    }
}

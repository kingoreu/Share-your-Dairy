package com.share.dairy.controller;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Alert;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.layout.Priority;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.Node;

import javafx.scene.control.ContentDisplay;
import javafx.geometry.Pos;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;

import java.net.URL;

public class DiaryHubController {

    @FXML private StackPane content;       // 허브 전체 루트 (키 필터 붙일 곳)
    @FXML private StackPane centerHolder;  // 중앙 교체 대상 컨테이너
    @FXML private Button shellFab;         // 허브 셸(fxml)에 숨겨둔 FAB(없으면 null)
    @FXML private VBox sidebar;            // 왼쪽 버튼 3개 담는 VBox (fx:id 없더라도 백업 룩업 적용)
    @FXML private Label titleLabel;        // 상단 제목

    // 중앙에 띄울 화면 경로들
    private static final String FXML_HUB   = "/fxml/diary/diary_hub/hub-list.fxml";
    private static final String FXML_MY    = "/fxml/diary/my_diary/my-diary-view.fxml";
    private static final String FXML_OUR   = "/fxml/diary/our_diary/our-diary-view.fxml";
    private static final String FXML_BUDDY = "/fxml/diary/buddy_diary/buddy-diary-view.fxml";

    // ESC → Home.fxml
    private final javafx.event.EventHandler<KeyEvent> escFilter = e -> {
        if (e.getCode() == KeyCode.ESCAPE) {
            goHome();
            e.consume();
        }
    };

    // 중복 로딩 방지
    private String activeFxml = null;

    public void initialize() {
        // ESC 필터 장착
        content.sceneProperty().addListener((obs, oldScene, scene) -> {
            if (oldScene != null) oldScene.removeEventFilter(KeyEvent.KEY_PRESSED, escFilter);
            if (scene != null)    scene.addEventFilter(KeyEvent.KEY_PRESSED, escFilter);
        });

        // FAB 기본 숨김
        if (shellFab != null) {
            shellFab.setVisible(false);
            shellFab.setManaged(false);
        }

        // 런타임 준비 후
        javafx.application.Platform.runLater(() -> {
            // FAB fx:id 불일치 대비: 런타임에 찾아 연결
            if (shellFab == null) {
                var n = content.lookup("#fabNew");
                if (n instanceof Button b) shellFab = b;
            }
            boolean onHub = FXML_HUB.equals(activeFxml) || activeFxml == null;
            showFab(onHub);

            // 시작 화면
            showDiaryHub();              // 중앙 교체
            setTitle("MY DIARY");        // 초기 제목

            // ─ 제목을 강제로 정중앙 유지 ─
            if (titleLabel != null) {
                titleLabel.setMaxWidth(Double.MAX_VALUE);
                String cur = titleLabel.getStyle();
                titleLabel.setStyle((cur == null ? "" : cur)
                        + ";-fx-alignment:center;-fx-text-alignment:center;");
                var p = titleLabel.getParent();
                if (p instanceof HBox hb) {
                    HBox.setHgrow(titleLabel, Priority.ALWAYS);
                    for (Node child : hb.getChildren()) {
                        if (child != titleLabel) HBox.setHgrow(child, Priority.NEVER);
                    }
                }
            }

            // 포커스
            content.setFocusTraversable(true);
            content.requestFocus();

            // 왼쪽 버튼 3개만 조정
            adjustSidebarButtons();

            // 사이드바 이모지 배지 부착
            applySidebarIcons();
        });
    }

    /* 좌/상단 버튼 핸들러 — 중앙만 교체 + 제목만 변경 */
    @FXML private void showDiaryHub()   { setTitle("MY DIARY");    setCenter(FXML_HUB); }
    @FXML private void showMyDiary()    { setTitle("MY DIARY");    setCenter(FXML_MY); }
    @FXML private void showOurDiary()   { setTitle("OUR DIARY");   setCenter(FXML_OUR); }
    @FXML private void showBuddyDiary() { setTitle("BUDDY DIARY"); setCenter(FXML_BUDDY); }

    // 상단 제목 변경(Null 안전)
    private void setTitle(String text) {
        if (titleLabel != null) titleLabel.setText(text);
    }

    // FAB 토글만 담당
    private void showFab(boolean show) {
        if (shellFab != null) {
            shellFab.setVisible(show);
            shellFab.setManaged(show);
            shellFab.setOnAction(show ? e -> showMyDiary() : null); // 허브 리스트에서만 동작
        }
    }

    private void setCenter(String fxml) {
    try {
        if (fxml != null && fxml.equals(activeFxml) && !centerHolder.getChildren().isEmpty()) {
            content.requestFocus();
            return;
        }

        var url = getClass().getResource(fxml);
        if (url == null) throw new IllegalStateException("FXML not found: " + fxml);

        // ★ FXMLLoader를 써서 컨트롤러를 얻는다
        FXMLLoader loader = new FXMLLoader(url);
        Parent node = loader.load();

        // ★ NEW DIARY 화면이면: 저장 후 MY DIARY(목록)으로 전환
        if (FXML_MY.equals(fxml)) {
            Object ctrl = loader.getController();
            if (ctrl instanceof MyDiaryController editor) {
                editor.setAfterSave(() ->
                    javafx.application.Platform.runLater(this::showDiaryHub)
                );
            }
        }

        centerHolder.getChildren().setAll(node);

        if (node instanceof javafx.scene.layout.Region r) {
            r.prefWidthProperty().bind(centerHolder.widthProperty());
            r.prefHeightProperty().bind(centerHolder.heightProperty());
        }

        activeFxml = fxml;

        boolean onHub = FXML_HUB.equals(fxml);
        showFab(onHub);

        content.requestFocus();
    } catch (Exception e) {
        e.printStackTrace();
    }
    }



    /** 홈 화면(Main.fxml)로 즉시 복귀 */
    private void goHome() {
        try {
            Parent home = FXMLLoader.load(getClass().getResource("/fxml/mainFrame/Main.fxml"));
            content.getScene().setRoot(home);
        } catch (Exception ex) {
            ex.printStackTrace();
            new Alert(Alert.AlertType.ERROR, "홈으로 이동 실패").showAndWait();
        }
    }

    /** 필요 시 사용: 사이드바의 "MY DIARY" 버튼을 리스트 화면으로 고정 */
    private void forceSidebarMyDiaryToHub() {
        javafx.application.Platform.runLater(() -> {
            if (content.getScene() == null) return;
            content.getScene().getRoot().lookupAll(".button").forEach(n -> {
                if (n instanceof javafx.scene.control.Button b) {
                    if ("MY DIARY".equals(b.getText())) {
                        b.setOnAction(e -> showDiaryHub());
                    }
                }
            });
        });
    }

    // 중복으로 떠있는 FAB(.fab)을 하나만 남기고 정리
    private void dedupeFab(boolean showOnHub) {
        if (shellFab != null) {
            shellFab.setVisible(showOnHub);
            shellFab.setManaged(showOnHub);
            shellFab.setOnAction(showOnHub ? e -> showMyDiary() : null);
        }
        if (content.getScene() != null) {
            content.getScene().getRoot().lookupAll(".fab").forEach(n -> {
                if (n != shellFab) {
                    n.setVisible(false);
                    n.setManaged(false);
                    if (n.getParent() instanceof Pane p) {
                        p.getChildren().remove(n);
                    }
                }
            });
        }
    }

    /* ================== 여기만 '추가/수정' ================== */
    private void adjustSidebarButtons() {
        try {
            // 1) Sidebar VBox 확보: @FXML 주입 실패 시 styleClass로 룩업
            VBox v = this.sidebar;
            if (v == null) {
                if (content == null || content.getScene() == null) return;
                var node = content.getScene().getRoot().lookup(".sidebar");
                if (node instanceof VBox) v = (VBox) node; else return;
            }

            // 2) BUDDY DIARY 아래 얇은 바 제거
            v.getChildren().removeIf(n -> n instanceof javafx.scene.control.Separator);

            // 3) 버튼 묶음을 아래로 — 맨 위에 스페이서 1개(중복 방지)
            if (v.getChildren().isEmpty() || !(v.getChildren().get(0) instanceof Region)) {
                Region spacer = new Region();
                VBox.setVgrow(spacer, Priority.ALWAYS);
                v.getChildren().add(0, spacer);
            }

            // 4) 간격/패딩 미세 조정
            v.setSpacing(32);
            v.setPadding(new Insets(12, 12, 4, 12));
        } catch (Exception ignore) { /* 다른 기능 영향 없도록 조용히 */ }
    }

    /** 사이드바 버튼에 이모지 배지만 붙인다(제목/레이아웃 영향 없음). */
    private void applySidebarIcons() {
        Runnable attach = () -> {
            if (content == null || (content.getScene() == null && sidebar == null)) return;

            // sidebar 자식 + 백업 lookup
            java.util.LinkedHashSet<Button> targets = new java.util.LinkedHashSet<>();
            if (sidebar != null) {
                for (Node n : sidebar.getChildren()) if (n instanceof Button b) targets.add(b);
            }
            var scene = content.getScene();
            if (scene != null) {
                scene.getRoot().lookupAll("#sidebar .button").forEach(n -> { if (n instanceof Button b) targets.add(b); });
                scene.getRoot().lookupAll(".sidebar .button").forEach(n -> { if (n instanceof Button b) targets.add(b); });
            }

            for (Button b : targets) {
                // 이미 래퍼 붙었으면 건너뜀
                if (b.getGraphic() != null && "sidebar-wrapper".equals(b.getGraphic().getUserData())) continue;

                // 이모지 매핑
                String emoji = switch (b.getText()) {
                    case "MY DIARY"    -> "📁";
                    case "OUR DIARY"   -> "👥";
                    case "BUDDY DIARY" -> "😊";
                    default -> null;
                };
                if (emoji == null) continue;

                // 배지(아이콘)
                Label icon = new Label(emoji);
                icon.setStyle("-fx-font-size:22;");
                StackPane badge = new StackPane(icon);
                badge.setMinSize(44, 44);
                badge.setMaxSize(44, 44);
                badge.setStyle(
                    "-fx-background-color:white;" +
                    "-fx-background-radius:14;" +
                    "-fx-effect:dropshadow(gaussian, rgba(0,0,0,0.12), 6,0,0,2);"
                );
                StackPane.setAlignment(icon, Pos.CENTER);

                // 캡션(버튼 text와 바인딩 → 항상 동일)
                Label caption = new Label();
                caption.textProperty().bind(b.textProperty());
                caption.setWrapText(true);
                caption.setAlignment(Pos.CENTER);
                caption.setStyle("-fx-padding: 6 0 0 0;"); // 살짝 간격

                // 그래픽 래퍼: 배지 + 캡션
                VBox wrapper = new VBox(6, badge, caption);
                wrapper.setAlignment(Pos.CENTER);
                wrapper.setUserData("sidebar-wrapper");

                // 버튼에 적용(그래픽만 사용)
                b.setGraphic(wrapper);
                b.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
                b.setGraphicTextGap(0);

                // 잘림 방지를 위한 높이 여유
                if (b.getMinHeight() < 112)  b.setMinHeight(112);
                if (b.getPrefHeight() < 120) b.setPrefHeight(120);

                // 텍스트 중앙정렬 유지
                String s = b.getStyle();
                b.setStyle((s == null ? "" : s) + "; -fx-text-alignment: center;");
            }
        };

        attach.run();
        javafx.application.Platform.runLater(attach);
    }
}

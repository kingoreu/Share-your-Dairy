package com.share.dairy.controller.login;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import javafx.scene.Node;

import java.io.IOException;
import java.util.List;

public class CharacterSelectController {

    @FXML
    private void onBackClicked(ActionEvent event) throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/login/SignUp.fxml"));
        Parent signUpRoot = loader.load();

        Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
        stage.getScene().setRoot(signUpRoot);
    }

    @FXML
    private void onSelectClicked(ActionEvent event) throws IOException {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/login/AfterSignUp.fxml"));
            Parent afterSignUpRoot = loader.load();

            System.out.println("onNextClicked called!!");
            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            stage.getScene().setRoot(afterSignUpRoot);
        } catch (Exception e) {
            e.printStackTrace();  // ❗️ 반드시 콘솔로 확인
            new Alert(Alert.AlertType.ERROR, "화면 전환 실패: " + e.getMessage()).showAndWait();
        }
    }

    @FXML private HBox characterBox;
    @FXML private Button prevBtn, nextBtn, confirmBtn;
    @FXML private Label pageInfoLabel;

    private List<String> imagePaths = List.of(
            "/character/zzuni.png",
            "/character/cat.png",
            "/character/hamster.png",
            "/character/raccoon.png",
            "/character/bear.png",
            "/character/deer.png",
            "/character/dog.png",
            "/character/duck.png",
            "/character/rabbit.png",
            "/character/richard.png",
            "/character/tako.png",
            "/character/wolf.png"
            // 12개 캐릭터
    );

    private static final int PAGE_SIZE = 4;
    private int pageCount;
    private int pageIndex = 0; // 0-based

    private final ToggleGroup group = new ToggleGroup();
    private String selectedPath = null;

    @FXML
    public void initialize() {
        pageCount = (int)Math.ceil(imagePaths.size() / (double)PAGE_SIZE);
        renderPage();

        prevBtn.setOnAction(e -> { if (pageIndex > 0) { pageIndex--; renderPage(); }});
        nextBtn.setOnAction(e -> { if (pageIndex < pageCount - 1) { pageIndex++; renderPage(); }});

        // 캐릭터 누르면 선택 버튼 활성화
        group.selectedToggleProperty().addListener((obs, oldT, newT) -> {
            if (newT != null) {
                selectedPath = (String) newT.getUserData();
                confirmBtn.setDisable(false);
            } else {
                selectedPath = null;
                confirmBtn.setDisable(true);
            }
        });

        // 확인 버튼 눌렀을 때 처리(원하는 동작 연결)
        if (confirmBtn != null) {
            confirmBtn.setOnAction(e -> {
                if (selectedPath != null) {
                    // TODO: 선택된 캐릭터 경로 사용
                    System.out.println("Selected character: " + selectedPath);
                    // 다음 화면으로 이동하거나, 상태 저장 등
                }
            });
        }

        // 키보드 화살표로도 이동
        characterBox.setOnKeyPressed(ev -> {
            switch (ev.getCode()) {
                case LEFT -> prevBtn.fire();
                case RIGHT -> nextBtn.fire();
            }
        });
    }

    private void renderPage() {
        characterBox.getChildren().clear();

        int from = pageIndex * PAGE_SIZE;
        int to   = Math.min(from + PAGE_SIZE, imagePaths.size());

        for (int i = from; i < to; i++) {
            ImageView iv = new ImageView(new Image(getClass().getResourceAsStream(imagePaths.get(i))));
            iv.setFitWidth(150);
            iv.setFitHeight(200);
            iv.setPreserveRatio(true);

            // 선택형으로 쓰려면 ToggleButton 사용 (주석 해제)
             ToggleButton btn = new ToggleButton();
             btn.setGraphic(iv);
             btn.setToggleGroup(group);
             btn.setUserData(imagePaths.get(i));
             btn.getStyleClass().add("character-button");
             btn.setFocusTraversable(true);

             characterBox.getChildren().add(btn);
        }

        pageInfoLabel.setText((pageIndex + 1) + "/" + pageCount);
        prevBtn.setDisable(pageIndex == 0);
        nextBtn.setDisable(pageIndex == pageCount - 1);

        // 페이지 전환 시 이전 선택 유지할건지?

    }
}

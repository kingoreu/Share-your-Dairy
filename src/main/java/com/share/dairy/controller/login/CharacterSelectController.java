package com.share.dairy.controller.login;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.share.dairy.auth.UserSession;
import com.share.dairy.dto.user.PendingSignUp;
import com.share.dairy.model.enums.CharacterType;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import javafx.scene.Node;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

public class CharacterSelectController {

    @FXML private HBox characterBox;
    @FXML private Button prevBtn, nextBtn, confirmBtn;
    @FXML private Label pageInfoLabel;

    private PendingSignUp pending;

    public void initData(PendingSignUp pendingSignUp) {
        this.pending = pendingSignUp;
    }

    private final List<String> imagePaths = CharacterType.getAllImagePaths();

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


        // 키보드 화살표로도 이동
        characterBox.setOnKeyPressed(ev -> {
            switch (ev.getCode()) {
                case LEFT -> prevBtn.fire();
                case RIGHT -> nextBtn.fire();
            }
        });
        // 페이지 렌더 직후 화살표 키 먹도록 포커스 요청
        characterBox.requestFocus();
    }

    @FXML
    private void onBackClicked(ActionEvent event) throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/login/SignUp.fxml"));
        Parent signUpRoot = loader.load();

        Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
        stage.getScene().setRoot(signUpRoot);
    }



    @FXML
    private void onSelectClicked(ActionEvent event) throws IOException {
        if (pending == null) {
            alert("회원정보가 없습니다. 처음부터 다시 진행해 주세요.");
            return;
        }
        if (selectedPath == null) {
            alert("캐릭터를 선택해 주세요.");
            return;
        }

        // 수정
        CharacterType selectedType = CharacterType.fromPath(selectedPath);

        String json = String.format(
                "{ \"nickname\":\"%s\", \"loginId\":\"%s\", \"password\":\"%s\", \"userEmail\":\"%s\", \"characterType\":\"%s\" }",
                esc(pending.nickname), esc(pending.loginId), esc(pending.password), esc(pending.userEmail), esc(selectedType.name())
        );

        confirmBtn.setDisable(true);

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:8080/api/users"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        client.sendAsync(req, HttpResponse.BodyHandlers.ofString())
                .whenComplete((res, err) -> javafx.application.Platform.runLater(() -> {
                    confirmBtn.setDisable(false);

                    if (err != null) {
                        alert("서버 연결 실패: " + err.getMessage());
                        return;
                    }

                    // 추가
                    if (res.statusCode() == 201) {
                        try {
                            // 서버 응답(JSON)을 파싱하여 userId 가져오기
                            ObjectMapper objectMapper = new ObjectMapper();
                            JsonNode rootNode = objectMapper.readTree(res.body());
                            long userId = rootNode.path("userId").asLong();

                            // UserSession에 올바른 userId와 기타 정보 저장
                            UserSession.set(new UserSession(
                                    userId, // 서버에서 받은 userId 사용
                                    pending.loginId,
                                    pending.nickname,
                                    pending.userEmail,
                                    selectedType
                            ));

                            goAfterSignUp(event);
                        } catch (IOException e) {
                            alert("서버 응답 파싱 실패: " + e.getMessage());
                        }
                    } else {
                        alert("회원가입 실패 (" + res.statusCode() + "): " + res.body());
                    }

//                    if (res.statusCode() == 201) {
//                        goAfterSignUp(event);
//                    } else {
//                        alert("회원가입 실패 (" + res.statusCode() + "): " + res.body());
//                    }
                }));
    }

    private void goAfterSignUp(ActionEvent event) {
        try {
            var loader = new FXMLLoader(getClass().getResource("/fxml/login/AfterSignUp.fxml"));
            Parent afterSignUpRoot = loader.load();
            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            stage.getScene().setRoot(afterSignUpRoot);
        } catch (IOException e) {
            alert("화면 전환 실패: " + e.getMessage());
        }
    }


    private void renderPage() {
        characterBox.getChildren().clear();

        int from = pageIndex * PAGE_SIZE;
        int to   = Math.min(from + PAGE_SIZE, imagePaths.size());

        for (int i = from; i < to; i++) {
            var is = getClass().getResourceAsStream(imagePaths.get(i));
            if (is == null) continue; // 리소스 경로 문제 시 안전하게 스킵
            ImageView iv = new ImageView(new Image(is));
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
        group.selectToggle(null);
    }


    private static String esc(String s){ return s.replace("\\","\\\\").replace("\"","\\\""); }
    private void alert(String msg) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("회원가입 오류");
        alert.setHeaderText(null);
        alert.setContentText(msg);

        alert.setGraphic(null);

        DialogPane dialogPane = alert.getDialogPane();
        dialogPane.getStylesheets().add(
                getClass().getResource("/css/login/alert.css").toExternalForm()
        );
        dialogPane.getStyleClass().add("custom-alert");

        alert.showAndWait();
    }
    // private void alert(String msg){ new Alert(Alert.AlertType.ERROR, msg).showAndWait(); }

}

package com.share.dairy.controller.login;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class LoginController {

    @FXML private TextField loginIdField;
    @FXML private PasswordField passwordField;
    @FXML private Button loginButton;

    @FXML
    private void onSignUpClicked(ActionEvent event) throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/login/SignUp.fxml"));
        Parent signUpRoot = loader.load();

        Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
        stage.getScene().setRoot(signUpRoot);
    }

    @FXML
    private void onLoginClicked(ActionEvent event) throws IOException {
        String id = trim(loginIdField.getText());
        String pw = trim(passwordField.getText());
        if (id.isEmpty() || pw.isEmpty()) { alert("아이디/비밀번호를 입력하세요."); return; }

        String json = String.format("{\"loginId\":\"%s\",\"password\":\"%s\"}", esc(id), esc(pw));

        loginButton.setDisable(true);

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:8080/api/users/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        client.sendAsync(req, HttpResponse.BodyHandlers.ofString())
                .whenComplete((res, err) -> javafx.application.Platform.runLater(() -> {
                    loginButton.setDisable(false);

                    if (err != null) { alert("서버 연결 실패: " + err.getMessage()); return; }

                    if (res.statusCode() == 200) {
                        // 필요 시 res.body()에서 userId/nickname 파싱해서 보관
                        goMain(event);
                    } else {
                        alert("로그인 실패 (" + res.statusCode() + "): " + res.body());
                    }
                }));
//        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/mainFrame/Main.fxml"));
//        Parent mainRoot = loader.load();
//
//        Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
//        stage.getScene().setRoot(mainRoot);



    }

    private void goMain(ActionEvent event) {
        try {
            var loader = new FXMLLoader(getClass().getResource("/fxml/mainFrame/Main.fxml"));
            Parent root = loader.load();
            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            stage.getScene().setRoot(root);
        } catch (Exception e) {
            alert("화면 전환 실패: " + e.getMessage());
        }
    }

    private static String trim(String s){ return s==null? "" : s.trim(); }
    private static String esc(String s){ return s.replace("\\","\\\\").replace("\"","\\\""); }
    private void alert(String msg){ new Alert(Alert.AlertType.ERROR, msg).showAndWait(); }

}

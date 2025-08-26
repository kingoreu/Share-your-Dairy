package com.share.dairy.controller.login;

import com.share.dairy.dto.user.PendingSignUp;
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

public class SignUpController {

    @FXML private TextField idField;
    @FXML private PasswordField confirmPasswordField;
    @FXML private PasswordField passwordField;
    @FXML private TextField nickNameField;
    @FXML private TextField emailField;
    // @FXML private Button nextButton;

    @FXML
    private void onLoginClicked(ActionEvent event) throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/login/Login.fxml"));
        Parent loginRoot = loader.load();

        Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
        stage.getScene().setRoot(loginRoot);
    }

    @FXML
    private void onNextClicked(ActionEvent event) throws IOException {
        String id = trim(idField.getText());
        String pw   = trim(passwordField.getText());
        String pw2  = trim(confirmPasswordField.getText());
        String nick = trim(nickNameField.getText());
        String email= trim(emailField.getText()).toLowerCase();

        if (id.isEmpty() || pw.isEmpty() || nick.isEmpty() || email.isEmpty()) {
            alert("모든 필드를 입력해주세요.");
            return;
        }

        if (!pw.equals(pw2)) {
            alert("비밀번호가 일치하지 않습니다.");
            return;
        }
        try {
            var loader = new FXMLLoader(getClass().getResource("/fxml/login/CharacterSelect.fxml"));
            Parent root = loader.load();

            // 컨트롤러 꺼내서 데이터 주입
            var pending = new PendingSignUp(nick, id, pw, email);
            CharacterSelectController ctrl = loader.getController();
            ctrl.initData(pending);

            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            stage.getScene().setRoot(root); // 또는 scene null이면 new Scene(root)
        } catch (IOException e) {
            alert("화면 전환 실패: " + e.getMessage());
        }
    }

    private static String trim(String s){ return s==null? "": s.trim(); }
    private void alert(String msg){ new Alert(Alert.AlertType.ERROR, msg).showAndWait(); }
}
package com.share.dairy.controller.login;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.share.dairy.dto.user.PendingSignUp;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.*;
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

    @FXML private Label idCheckLabel;
    @FXML private Label emailCheckLabel;

    private final HttpClient client = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    // 중복확인 버튼 누르면 나오는 라벨 바인딩
    @FXML
    private void initialize() {
        idCheckLabel.managedProperty().bind(idCheckLabel.textProperty().isNotEmpty());
        idCheckLabel.visibleProperty().bind(idCheckLabel.textProperty().isNotEmpty());

        emailCheckLabel.managedProperty().bind(emailCheckLabel.textProperty().isNotEmpty());
        emailCheckLabel.visibleProperty().bind(emailCheckLabel.textProperty().isNotEmpty());
    }

    private void setLabelStatus(Label label, boolean success, String message) {
        label.getStyleClass().removeAll("error", "success"); // 이전 상태 제거
        label.getStyleClass().add(success ? "success" : "error");
        label.setText(message);
    }

    private boolean isValidEmail(String email) {
        return email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$")
                && email.length() <= 50;
    }

    private boolean isValidLoginId(String id) {
        // 영문자 또는 숫자, 길이 2자 이상
        return id.matches("^[a-z0-9_]{2,20}$");
    }


    @FXML
    private void onCheckIdClicked() {
        String loginId = idField.getText().trim();
        if (loginId.isEmpty()) {
            setLabelStatus(idCheckLabel, false, "아이디를 입력하세요.");
            return;
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:8080/api/users/check_id?loginId=" + loginId))
                .GET()
                .build();

        client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body)
                .thenAccept(body -> javafx.application.Platform.runLater(() -> {
                    try {
                        JsonNode json = mapper.readTree(body);
                        boolean exists = json.get("exists").asBoolean();
                        if (exists) {
                            setLabelStatus(idCheckLabel, false, "이미 사용 중인 아이디입니다.");
                        } else {
                            setLabelStatus(idCheckLabel, true, "사용 가능한 아이디입니다.");
                        }
                    } catch (Exception e) {
                        setLabelStatus(idCheckLabel, false, "응답 파싱 오류");
                    }
                }))
                .exceptionally(ex -> {
                    javafx.application.Platform.runLater(() -> setLabelStatus(idCheckLabel, false, "서버 오류"));
                    return null;
                });
    }

    @FXML
    private void onCheckEmailClicked() {
        String email = emailField.getText().trim();
        if (email.isEmpty()) {
            setLabelStatus(emailCheckLabel, false, "이메일을 입력하세요.");
            return;
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:8080/api/users/check_email?email=" + email))
                .GET()
                .build();

        client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body)
                .thenAccept(body -> javafx.application.Platform.runLater(() -> {
                    try {
                        JsonNode json = mapper.readTree(body);
                        boolean exists = json.get("exists").asBoolean();
                        if (exists) {
                            setLabelStatus(emailCheckLabel, false, "이미 사용 중인 이메일입니다.");
                        } else {
                            setLabelStatus(emailCheckLabel, true, "사용 가능한 이메일입니다.");
                        }
                    } catch (Exception e) {
                        emailCheckLabel.setText("응답 파싱 오류");
                    }
                }))
                .exceptionally(ex -> {
                    javafx.application.Platform.runLater(() -> emailCheckLabel.setText("서버 오류"));
                    return null;
                });
    }


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

        // 비밀번호 길이 검증
        if (pw.length() < 8) {
            alert("비밀번호는 8자 이상이어야 합니다.");
            return;
        }

        // 아이디 형식 검증
        if (!isValidLoginId(id)) {
            alert("아이디 형식이 올바르지 않습니다.\n(영문/숫자 조합, 2~12자)");
            return;
        }

        // 닉네임 길이 검증
        if (nick.length() < 2 || nick.length() > 50) {
            alert("닉네임은 2자 이상 50자 이하로 입력해주세요.");
            return;
        }

        // 이메일 형식 검증
        if (!isValidEmail(email)) {
            alert("이메일 형식이 올바르지 않습니다.");
            return;
        }

        // 캐릭터 선택 화면으로 이동
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

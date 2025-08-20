package com.share.dairy.controller.login;

import com.share.dairy.auth.UserSession;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LoginController {

    @FXML private TextField idField;
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
    private void onLoginClicked(ActionEvent event) {
        String id = trim(idField.getText());
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
                        // ✅ 로그인 성공 → 응답 JSON에서 사용자 정보 추출해 세션에 저장
                        String body = res.body();

                        long   userId        = parseLong(jget(body, "userId", "id"), -1);
                        String loginId       = firstNonEmpty(jget(body, "loginId", "username"), id);
                        String nickname      = firstNonEmpty(jget(body, "nickname", "nick"), "");
                        String email         = firstNonEmpty(jget(body, "userEmail", "email"), "");
                        String characterType = firstNonEmpty(jget(body, "characterType", "character_type"), "RACCOON");

                        UserSession.set(new UserSession(userId, loginId, nickname, email, characterType));

                        goMain(event);
                    } else {
                        alert("로그인 실패 (" + res.statusCode() + "): " + res.body());
                    }
                }));
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

    // ───────── helpers ─────────
    private static String trim(String s){ return s==null? "" : s.trim(); }
    private static String esc(String s){ return s.replace("\\","\\\\").replace("\"","\\\""); }
    private static void alert(String msg){ new Alert(Alert.AlertType.ERROR, msg).showAndWait(); }

    private static String firstNonEmpty(String a, String b){
        return (a != null && !a.isBlank()) ? a : b;
    }
    private static long parseLong(String s, long def){
        try { return (s==null || s.isBlank()) ? def : Long.parseLong(s.trim()); }
        catch (Exception e) { return def; }
    }

    /**
     * 아주 가벼운 JSON 값 추출기.
     * - {"key":"value"} 또는 {"key":123}
     * - {"data": {...}} 래핑도 지원
     */
    private static String jget(String json, String... keys) {
        if (json == null) return null;

        // data 래핑 처리
        Matcher md = Pattern.compile("\"data\"\\s*:\\s*\\{([\\s\\S]*?)\\}").matcher(json);
        if (md.find()) {
            String inner = md.group(1);
            String v = jget(inner, keys);
            if (v != null) return v;
        }

        for (String key : keys) {
            String k = Pattern.quote(key);
            Pattern p = Pattern.compile("\"" + k + "\"\\s*:\\s*(?:\"([^\"]*)\"|(\\d+))", Pattern.CASE_INSENSITIVE);
            Matcher m = p.matcher(json);
            if (m.find()) {
                String s = (m.group(1) != null) ? m.group(1) : m.group(2);
                return (s != null) ? s.trim() : null;
            }
        }
        return null;
    }
}

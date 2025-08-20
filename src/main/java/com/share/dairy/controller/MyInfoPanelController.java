package com.share.dairy.controller;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;

import java.io.InputStream;
import java.net.URL;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;

public class MyInfoPanelController {

    // ===== FXML 바인딩 =====
    @FXML private StackPane card;
    @FXML private ImageView imgCharacter;

    @FXML private TextField tfId;
    @FXML private PasswordField pfPassword;
    @FXML private TextField tfEmail;
    @FXML private TextField tfNickname;
    @FXML private ComboBox<String> cbCharacter;

    @FXML private Button btnEdit;
    @FXML private Label  lblHint;

    private boolean editing = false;

    // ===== 초기화 =====
    @FXML
    private void initialize() {
        // 캐릭터 이미지가 카드 폭에 맞게 줄어들도록
        if (card != null && imgCharacter != null) {
            imgCharacter.fitWidthProperty().bind(card.widthProperty().subtract(36));
            imgCharacter.setPreserveRatio(true);
        }

        // 캐릭터 옵션 (원하는 값으로 교체 가능)
        cbCharacter.getItems().setAll("Raccoon", "Dog", "Cat");
        cbCharacter.getSelectionModel().selectedItemProperty().addListener(
                (obs, o, n) -> setCharacterPreviewByName(n)
        );

        // 사용자 데이터 로딩(스텁)
        loadMyInfo();
        setEditing(false);
    }

    // ===== 화면 전환 (좌측 네비) =====
    @FXML private void goHome(ActionEvent e)      { /* Router.navigate("home"); */ }
    @FXML private void goAddFriends(ActionEvent e){ switchTo("/fxml/FriendList/AddFriendsPanel.fxml", (Node)e.getSource()); }
    @FXML private void goBuddyList(ActionEvent e) { switchTo("/fxml/FriendList/FriendListPanel.fxml", (Node)e.getSource()); }

    private void switchTo(String fxmlPath, Node trigger){
        try{
            URL url = getClass().getResource(fxmlPath);
            if (url == null) { hint("화면 파일을 찾을 수 없어요: " + fxmlPath); return; }
            Parent root = FXMLLoader.load(url);
            trigger.getScene().setRoot(root);
        }catch(Exception ex){
            ex.printStackTrace();
            hint("화면 전환 실패: " + ex.getMessage());
        }
    }

    // ===== Edit/Save 토글 =====
    @FXML
    private void onEditToggle(ActionEvent e) {
        if (!editing) {
            // 보기 → 수정 모드
            setEditing(true);
            lblHint.setText("수정 후 Save를 눌러 저장하세요.");
            btnEdit.setText("Save");
        } else {
            // 저장 시도
            if (!validateInputs()) return;
            boolean ok = updateMyInfo(); // DB 붙이면 이 메서드 구현
            if (ok) {
                setEditing(false);
                btnEdit.setText("Edit");
                lblHint.setText("저장 완료!");
            } else {
                lblHint.setText("저장 중 오류가 발생했어요. 잠시 후 다시 시도해 주세요.");
            }
        }
    }

    private void setEditing(boolean on) {
        this.editing = on;
        boolean e = on;

        // ID는 보통 수정 불가
        tfId.setEditable(false);
        pfPassword.setEditable(e);
        tfEmail.setEditable(e);
        tfNickname.setEditable(e);
        cbCharacter.setDisable(!e);
    }

    // ===== 데이터 바인딩 =====
    private void loadMyInfo() {
        // === 스텁 데이터 (나중에 DB에서 가져오세요) ===
        tfId.setText("User");
        pfPassword.setText("1234");
        tfEmail.setText("value@domain.com");
        tfNickname.setText("구리구리");
        cbCharacter.getSelectionModel().select("Raccoon");
        setCharacterPreviewByName("Raccoon");
    }

    private boolean updateMyInfo() {
        // === 스텁: 성공 처리 ===
        // TODO: DB 업데이트 쿼리로 교체
        return true;
    }

    private boolean validateInputs() {
        if (tfEmail.getText() == null || !tfEmail.getText().contains("@")) {
            hint("이메일 형식이 올바르지 않아요.");
            return false;
        }
        if (pfPassword.getText() == null || pfPassword.getText().length() < 4) {
            hint("비밀번호는 4자 이상으로 입력하세요.");
            return false;
        }
        return true;
    }

    // ===== 미리보기 이미지 =====
    private void setCharacterPreviewByName(String name) {
        String path;
        if ("Raccoon".equalsIgnoreCase(name))      path = "/characters/raccoon.png";
        else if ("Dog".equalsIgnoreCase(name))     path = "/characters/dog.png";
        else if ("Cat".equalsIgnoreCase(name))     path = "/characters/cat.png";
        else                                       path = "/characters/character_default.png";

        try (InputStream in = getClass().getResourceAsStream(path)) {
            if (in != null) imgCharacter.setImage(new Image(in));
            else            imgCharacter.setImage(null);
        } catch (Exception ignored) {}
    }

    // ===== 공용 =====
    private void hint(String s){ if (lblHint != null) lblHint.setText(s); }

    // 아래 둘은 DB 붙일 때 쓰세요(스텁 자리표시자)
    @SuppressWarnings("unused")
    private Connection getConnection() throws SQLException { throw new SQLException("DatabaseManager 구현 필요"); }
    @SuppressWarnings("unused")
    private void close(Connection c){ try { if (c!=null) c.close(); } catch (Exception ignored) {} }
}

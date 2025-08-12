package com.share.dairy.controller;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

/**
 * MY Info 화면 컨트롤러
 * - 초기에는 읽기 전용(편집 불가)
 * - Edit 클릭 시 입력 가능, 버튼 텍스트가 Save로 바뀜(저장 로직은 TODO)
 */
public class MyInfoController {

    @FXML private ImageView avatarView;

    @FXML private TextField idField;
    @FXML private PasswordField pwField;
    @FXML private TextField emailField;
    @FXML private TextField nicknameField;
    @FXML private TextField characterField;

    @FXML private Button editBtn;

    private boolean editing = false;

    @FXML
    private void initialize() {
        // 기본 이미지 + 샘플 값
        avatarView.setImage(load("/common_images/myinfo-icon.png"));
        idField.setText("User");
        pwField.setText("1234");
        emailField.setText("Value@.com");
        nicknameField.setText("구리구리");
        characterField.setText("Raccoon");

        // 처음엔 읽기 전용
        setEditable(false);
    }

    @FXML
    private void onEdit() {
        if (!editing) {
            // 편집 모드 ON
            setEditable(true);
            editBtn.setText("Save");
        } else {
            // 저장(추후 DB 연동 위치)
            System.out.printf("[MY-INFO] save: id=%s, email=%s, nick=%s, char=%s%n",
                    idField.getText(), emailField.getText(),
                    nicknameField.getText(), characterField.getText());

            setEditable(false);
            editBtn.setText("Edit");
        }
        editing = !editing;
    }

    /** 편집 가능 여부 토글 (모양은 그대로 두고 타이핑만 가능/불가) */
    private void setEditable(boolean enabled) {
        idField.setEditable(enabled);
        pwField.setEditable(enabled);
        emailField.setEditable(enabled);
        nicknameField.setEditable(enabled);
        characterField.setEditable(enabled);
    }

    private Image load(String cpPath) {
        var url = getClass().getResource(cpPath);
        if (url == null) throw new IllegalStateException("resource not found: " + cpPath);
        return new Image(url.toExternalForm());
    }
}

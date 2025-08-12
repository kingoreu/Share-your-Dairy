package com.share.dairy.controller;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

/**
 * ADD Friends 화면 컨트롤러
 * - 지금은 UI 데모용: Search/ADD 버튼은 로그만 출력
 * - 나중에 서비스 붙이면 onSearch에서 조회 → 아바타/버튼상태 갱신
 */
public class AddFriendsController {

    @FXML private ImageView avatarView;
    @FXML private TextField idField;
    @FXML private Button searchBtn, addBtn;

    @FXML
    private void initialize() {
        // 기본 프리뷰 이미지(없으면 예외 던져서 경로 문제 바로 알기)
        avatarView.setImage(load("/common_images/buddylist-icon.png"));

        // 초깃값: ADD는 바로 눌러도 되게(원하면 disable true로 바꿔도 OK)
        addBtn.setDisable(false);
    }

    @FXML
    private void onSearch() {
        String q = idField.getText();
        System.out.println("[ADD-FRIENDS] search: " + q);
        // TODO 실제 조회 후 결과 이미지/상태 갱신
        // avatarView.setImage(...);
    }

    @FXML
    private void onAdd() {
        String q = idField.getText();
        System.out.println("[ADD-FRIENDS] add: " + q);
        // TODO 실제 친구 추가 처리
    }

    // --- util: 클래스패스 이미지 로더 ---
    private Image load(String cpPath) {
        var url = getClass().getResource(cpPath);
        if (url == null) throw new IllegalStateException("resource not found: " + cpPath);
        return new Image(url.toExternalForm());
    }
}

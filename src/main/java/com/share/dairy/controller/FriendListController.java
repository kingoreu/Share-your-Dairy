package com.share.dairy.controller;

import com.share.dairy.model.Friend;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.TilePane;

import java.io.IOException;

/**
 * 친구 리스트 컨트롤러
 * - 외부에서 ObservableList<Friend>를 주입(setFriends) 받는다 (샘플데이터 없음)
 * - 선택/삭제 버튼은 선택 상태에 따라 enable/disable
 * - Stage/Scene 의존 없이 "Parent 뷰"만 제공 → 공용 쉘에 쉽게 삽입 가능
 */
public class FriendListController {

    @FXML private TilePane tile;
    @FXML private ScrollPane scroll;
    @FXML private Button selectBtn, deleteBtn;

    private FriendItemController selectedItem;

    @FXML
    private void initialize() {
        // ScrollPane 너비에 맞춰 타일의 선호폭을 약간 줄여서 수직 스크롤바 여유 확보
        tile.prefWidthProperty().bind(scroll.widthProperty().subtract(16));
        tile.setPrefColumns(2);
        updateButtons();
    }

    /** 외부(메인/공용)에서 데이터 주입 */
    public void setFriends(ObservableList<Friend> friends) {
        tile.getChildren().clear();
        if (friends == null || friends.isEmpty()) {
            selectedItem = null;
            updateButtons();
            return;
        }
        for (Friend f : friends) addItem(f);
        updateButtons();
    }

    private void addItem(Friend f) {
        try {
            FXMLLoader fx = new FXMLLoader(getClass().getResource("/fxml/FriendItem.fxml"));
            Node node = fx.load();
            FriendItemController item = fx.getController();
            item.setFriend(f);

            // 클릭 시 단일 선택 토글
            node.setOnMouseClicked(e -> select(item));

            tile.getChildren().add(node);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load FriendItem.fxml", e);
        }
    }

    private void select(FriendItemController item) {
        if (selectedItem != null && selectedItem != item) selectedItem.setSelected(false);
        boolean next = !item.isSelected();
        item.setSelected(next);
        selectedItem = next ? item : null;
        updateButtons();
    }

    private void updateButtons() {
        boolean has = selectedItem != null;
        selectBtn.setDisable(!has);
        deleteBtn.setDisable(!has);
    }

    @FXML private void onSelect() {
        if (selectedItem == null) return;
        // TODO: 필요 시 외부 콜백으로 선택 알림 전달하도록 확장
        System.out.println("Select: " + selectedItem.getFriend().getName());
    }

    @FXML private void onDelete() {
        if (selectedItem == null) return;
        System.out.println("Delete requested: " + selectedItem.getFriend().getName());
    }
}

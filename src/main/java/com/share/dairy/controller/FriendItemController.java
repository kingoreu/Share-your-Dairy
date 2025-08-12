package com.share.dairy.controller;

import com.share.dairy.model.Friend;
import javafx.css.PseudoClass;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.layout.AnchorPane;

/**
 * 친구 카드(셀) 컨트롤러
 * - 선택 상태를 pseudo-class(:selected)로 노출 → CSS에서 스타일링
 */
public class FriendItemController {
    private static final PseudoClass SELECTED = PseudoClass.getPseudoClass("selected");

    @FXML private AnchorPane root;
    @FXML private ImageView avatarView;
    @FXML private Label nameLabel;

    private boolean selected;
    private Friend friend;

    public void setFriend(Friend f) {
        this.friend = f;
        nameLabel.setText(f.getName());
        avatarView.setImage(f.getAvatar());
    }

    public AnchorPane getRoot() { return root; }
    public Friend getFriend() { return friend; }

    public boolean isSelected() { return selected; }
    public void setSelected(boolean s) {
        selected = s;
        root.pseudoClassStateChanged(SELECTED, s);
    }
}

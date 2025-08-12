package com.share.dairy.controller;

import com.share.dairy.ui.SidebarItem;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;

import java.util.function.Consumer;

/**
 * 공용 쉘 컨트롤러
 * - 헤더 가운데 제목(pageTitle) 갱신 API 제공
 * - 좌측 메뉴/홈 클릭은 외부 네비 콜백으로 전달
 * - 중앙 영역은 setContent(view)로 교체
 */
public class CommonController {

    // 헤더
    @FXML private Button homeBtn;
    @FXML private ImageView homeIcon;
    @FXML private Label pageTitle;        // ★ 가운데 제목

    // 좌측 메뉴
    @FXML private SidebarItem myInfoItem;
    @FXML private SidebarItem addFriendsItem;
    @FXML private SidebarItem buddyListItem;

    // 중앙 교체 영역
    @FXML private StackPane contentRoot;

    private Consumer<String> navigateHandler;

    @FXML
    private void initialize() {
        // 헤더 홈 아이콘
        homeIcon.setImage(load("/common_images/home-icon.png"));
        homeBtn.setOnAction(e -> fire("HOME"));

        // 좌측 메뉴
        myInfoItem.setText("MY Info");
        myInfoItem.setIcon(load("/common_images/myinfo-icon.png"));
        myInfoItem.setTarget("MY_INFO");
        myInfoItem.setOnAction(e -> fire(myInfoItem.getTarget()));

        addFriendsItem.setText("ADD Friends");
        addFriendsItem.setIcon(load("/common_images/addfriends-icon.png"));
        addFriendsItem.setTarget("ADD_FRIENDS");
        addFriendsItem.setOnAction(e -> fire(addFriendsItem.getTarget()));

        buddyListItem.setText("BUDDY List");
        buddyListItem.setIcon(load("/common_images/buddylist-icon.png"));
        buddyListItem.setTarget("BUDDY_LIST");
        buddyListItem.setOnAction(e -> fire(buddyListItem.getTarget()));
    }

    public void setOnNavigate(Consumer<String> handler) { this.navigateHandler = handler; }

    public void setContent(Node view) { contentRoot.getChildren().setAll(view); }

    public void setActive(String target) {
        myInfoItem.setSelected("MY_INFO".equals(target));
        addFriendsItem.setSelected("ADD_FRIENDS".equals(target));
        buddyListItem.setSelected("BUDDY_LIST".equals(target));
    }

    /** ★ 헤더 가운데 제목 바꾸기 */
    public void setHeaderTitle(String title) {
        pageTitle.setText(title != null ? title : "");
    }

    // util
    private Image load(String cpPath) {
        var url = getClass().getResource(cpPath);
        if (url == null) throw new IllegalStateException("resource not found: " + cpPath);
        return new Image(url.toExternalForm());
    }
    private void fire(String target) { if (navigateHandler != null) navigateHandler.accept(target); }
}

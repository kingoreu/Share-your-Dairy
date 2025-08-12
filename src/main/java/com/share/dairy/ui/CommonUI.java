// src/main/java/com/share/dairy/ui/CommonUI.java
package com.share.dairy.ui;

import com.share.dairy.controller.CommonController;
import com.share.dairy.controller.FriendListController;
import com.share.dairy.controller.AddFriendsController; // 있다면 사용, 없으면 제거
import com.share.dairy.model.Friend;
import javafx.application.Application;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;

import java.io.IOException;

/**
 * 공용 쉘 실행용 런처 (뷰/컨트롤러 캐싱 버전)
 * - B 옵션: 화면을 전환할 때마다 FXML을 매번 다시 로드하지 않고, 최초 1회만 로드해서 재사용.
 * - 이 방식이면 FriendList에 setFriends(...)를 "처음 한 번만" 호출해도
 *   다시 돌아왔을 때 목록이 비지 않는다.
 */
public class CommonUI extends Application {

    // ─────────────────────────────────────────────────────────────
    // 공용(쉘) 뷰/컨트롤러
    private Parent shell;
    private CommonController common;

    // 친구 목록: 여러 화면이 공유하는 모델(동일 인스턴스 유지)
    private final ObservableList<Friend> friends = FXCollections.observableArrayList();

    // 캐시: Buddy List 화면
    private Parent buddyView;                 // 뷰(Parent)
    private FriendListController buddyCtrl;   // 컨트롤러

    // 캐시: Add Friends 화면 (컨트롤러 필요 없으면 Parent만 캐싱해도 OK)
    private Parent addView;
    private AddFriendsController addCtrl;     // 컨트롤러가 없다면 제거

    // 아이콘 샘플 이미지 (공용 리소스에서 로드)
    private Image sampleAvatar;

    private Parent myView;
    private com.share.dairy.controller.MyInfoController myCtrl;

    // ─────────────────────────────────────────────────────────────

    @Override
    public void start(Stage stage) throws Exception {
        // 0) 리소스 준비 (이미지 경로가 틀리면 즉시 예외)
        sampleAvatar = loadImage("/common_images/buddylist-icon.png");

        // 1) 공용 쉘 로드
        FXMLLoader shellLoader = new FXMLLoader(getClass().getResource("/fxml/common.fxml"));
        shell = shellLoader.load();
        common = shellLoader.getController();

        // 2) 네비게이션 핸들러 연결 (전환 시 캐시된 뷰를 제공)
        common.setOnNavigate(target -> {
            try {
                switch (target) {
                    case "BUDDY_LIST" -> {
                        common.setContent(loadBuddyView());      // 캐시 사용 중이라 가정
                        common.setHeaderTitle("Buddy List");     // ★ 가운데 제목
                    }
                    case "ADD_FRIENDS" -> {
                        common.setContent(loadAddView());
                        common.setHeaderTitle("Add Friends");    // ★ 가운데 제목
                    }
                    case "MY_INFO" -> {
                        // common.setContent(...);
                        common.setContent(loadMyView());
                        common.setHeaderTitle("MY Info");   // 헤더 중앙 제목
                    }
                    case "HOME" -> {
                        // 메인으로 전환 시 원하는 제목으로
                        common.setHeaderTitle("share your dAIry");
                    }
                }
                common.setActive(target);
            } catch (IOException e) { throw new RuntimeException(e); }
        });


        // 3) 초기 데이터 세팅 (예시)
        friends.setAll(
                new Friend("K.K",   sampleAvatar),
                new Friend("Naki",  sampleAvatar),
                new Friend("Guide", sampleAvatar),
                new Friend("K.K",   sampleAvatar)
        );

        // 4) 첫 화면: Buddy List (캐시 통해 로드하므로 setFriends는 내부에서 최초 1회만 호출)
        common.setContent(loadBuddyView());
        common.setActive("BUDDY_LIST");
        common.setHeaderTitle("Buddy List");

        // 5) 씬 구성 + 공용 CSS 적용
        Scene scene = new Scene(shell, 1120, 720);
        scene.getStylesheets().add(getClass().getResource("/css/common.css").toExternalForm());
        stage.setScene(scene);
        stage.setTitle("share your dAIry");
        stage.show();
    }

    /**
     * Buddy List 화면을 캐싱해서 반환.
     * - 최초 호출 때만 FXML 로드 + 컨트롤러 획득 + setFriends(...) 주입.
     * - 이후 호출은 같은 Parent를 그대로 반환(상태 유지).
     */
    private Parent loadBuddyView() throws IOException {
        if (buddyView == null) {
            FXMLLoader fx = new FXMLLoader(getClass().getResource("/fxml/FriendListView.fxml"));
            buddyView = fx.load();
            buddyCtrl = fx.getController();

            // ★ 여기서 '한 번만' 데이터 주입
            buddyCtrl.setFriends(friends);
        }
        return buddyView;
    }

    /**
     * Add Friends 화면을 캐싱해서 반환.
     * - 컨트롤러가 있으면 획득해서, 필요 시 콜백/공유리스트를 주입할 수 있다.
     *   (아래는 예시: onAdd에서 friends에 추가 → Buddy 화면에도 즉시 반영됨.)
     */
    private Parent loadAddView() throws IOException {
        if (addView == null) {
            FXMLLoader fx = new FXMLLoader(getClass().getResource("/fxml/AddFriendsView.fxml"));
            addView = fx.load();
            addCtrl = fx.getController();

            // (선택) Add 버튼이 실제로 친구를 추가하게 만들고 싶다면:
            // addCtrl.setOnAdd(nameOrId -> {
            //     friends.add(new Friend(nameOrId, sampleAvatar));
            // });
            //
            // 또는 addCtrl.setFriends(friends) 식으로 동일 리스트를 넘겨서
            // 컨트롤러 내부에서 friends.add(...) 하게 해도 OK.
        }
        return addView;
    }

    private Parent loadMyView() throws IOException {
        if (myView == null) {
            var fx = new FXMLLoader(getClass().getResource("/fxml/MyInfoView.fxml"));
            myView = fx.load();
            myCtrl = fx.getController();
            // 필요 시 사용자 데이터 주입 API를 만들어 넘길 수 있음
            // myCtrl.setProfile(...);
        }
        return myView;
    }

    // ─────────────────────────────────────────────────────────────
    // 유틸
    private Image loadImage(String cpPath) {
        var url = getClass().getResource(cpPath);
        if (url == null) throw new IllegalStateException("resource not found: " + cpPath);
        return new Image(url.toExternalForm());
    }
}

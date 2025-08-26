package com.share.dairy.controller.character;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.share.dairy.auth.UserSession;
import com.share.dairy.controller.MainController;
import javafx.fxml.FXML;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class CharacterPaneController implements MainController.NeedsOverlayHost{
    @FXML private ImageView characterImg;
    private MainController.OverlayHost overlayHost;

    private static CharacterPaneController instance;

    public static CharacterPaneController getInstance() {
        return instance;
    }

    @Override
    public void setOverlayHost(MainController.OverlayHost host) {
        this.overlayHost = host;
    }

    public void updateCharacter(String pathOrUrl) {
        try {
            if (pathOrUrl != null) {
                // "/media/..." → 서버 URL로 변환
                if (pathOrUrl.startsWith("/media/")) {
                    pathOrUrl = "http://localhost:8080" + pathOrUrl;
                }
                // 로컬 파일 또는 URL 가능
                Image img = new Image(pathOrUrl, true);
                characterImg.setImage(img);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        System.out.println("[CharacterPane] image loaded=" + characterImg.getImage());
        System.out.println("[CharacterPane] imageView bounds=" + characterImg.getBoundsInParent());

    }


    @FXML
    public void initialize() {
        instance = this;

        UserSession user = UserSession.get();
        if (user == null) return;


        // refreshCharacter();

        // 1) 기본 캐릭터 (enum 기반)
        var type = user.getCharacterType();
        var defaultPath = type.getImagePath();
        var is = getClass().getResourceAsStream(defaultPath);
        if (is != null) {
            characterImg.setImage(new Image(is));
        }

        // 2) DB에서 최신 생성 캐릭터 있으면 덮어쓰기
        // loadLatestGeneratedCharacter(user.getUserId());
        loadLatestGeneratedCharacter(user);

    }


//    public void refreshCharacter() {
//        try {
//            UserSession user = UserSession.get();
//            if (user == null) return;
//
//            // 1) 기본 캐릭터 먼저 그림
//            var type = user.getCharacterType();
//            System.out.println("[CharacterPane] refreshCharacter: userType=" + type);
//
//            var defaultPath = type.getImagePath();
//            var is = getClass().getResourceAsStream(defaultPath);
//            if (is != null) {
//                Image img = new Image(is);
//                characterImg.setImage(img);
//                System.out.println("[CharacterPane] 기본 캐릭터 로드 성공: " + defaultPath + ", image=" + img);
//            } else {
//                System.err.println("[CharacterPane] 기본 캐릭터 리소스 없음: " + defaultPath);
//            }
//
//            // 2) 최신 생성 이미지 (같은 타입일 경우만 덮어쓰기)
//            loadLatestGeneratedCharacter(user);
//
//            System.out.println("[CharacterPane] 캐릭터 새로고침 완료");
//        } catch (Exception e) {
//            System.err.println("[CharacterPane] refreshCharacter 오류: " + e.getMessage());
//            e.printStackTrace();
//        }
//    }
//
//    private void loadLatestGeneratedCharacter(UserSession user) {
//        try {
//            // String apiUrl = "http://localhost:8080/api/character_keyword_images/latest?userId=" + userId;
//            String apiUrl = "http://localhost:8080/api/character_keyword_images/latest?userId=" + user.getUserId();
//            HttpURLConnection con = (HttpURLConnection) new URL(apiUrl).openConnection();
//            con.setRequestProperty("Accept", "application/json");
//
//            if (con.getResponseCode() == 200) {
//                try (var in = new BufferedReader(new InputStreamReader(con.getInputStream()))) {
//                    ObjectMapper mapper = new ObjectMapper();
//                    JsonNode root = mapper.readTree(in);
//                    String path = root.path("pathOrUrl").asText(null);
//                    String createdAt = root.path("createdAt").asText(null);
//
//                    System.out.println("[CharacterPane] latest path=" + path + ", createdAt=" + createdAt);
//                    System.out.println("[CharacterPane] userUpdatedAt=" + user.getUserUpdatedAt());
//
//                    if (path != null && !path.isBlank()) {
//                        boolean applyImage = true;
//
//                        // 1) userUpdatedAt 과 비교해서, 이미지 생성시각이 더 이전이면 무시
//                        if (createdAt != null && user.getUserUpdatedAt() != null) {
//                            LocalDateTime imageCreated;
//                            try {
//                                imageCreated = LocalDateTime.parse(createdAt); // ISO-8601
//                            } catch (Exception ex) {
//                                DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
//                                imageCreated = LocalDateTime.parse(createdAt, fmt); // MySQL 형식
//                            }
//
//                            if (imageCreated.isBefore(user.getUserUpdatedAt())) {
//                                System.out.println("[CharacterPane] 캐릭터 변경 이후 이미지 → 무시");
//                                applyImage = false;
//                            }
//                        }
//
//                        // 2) 조건 통과 → 이미지 적용
//                        if (applyImage) {
//                            updateCharacter(path);
//                            System.out.println("[CharacterPane] 생성 이미지 적용됨");
//                        }
//                        // updateCharacter(path);
//                        System.out.println("[CharacterPane] 생성 이미지 적용됨");
//                    }
//
//                }
//            } else {
//                System.out.println("[CharacterPane] 최신 생성 캐릭터 없음 → 기본 유지");
//            }
//        } catch (Exception e) {
//            System.err.println("[CharacterPane] 캐릭터 로딩 실패: " + e.getMessage());
//        }
//    }

    private void loadLatestGeneratedCharacter(UserSession user) {
        try {
            String apiUrl = "http://localhost:8080/api/character_keyword_images/latest?userId=" + user.getUserId();
            HttpURLConnection con = (HttpURLConnection) new URL(apiUrl).openConnection();
            con.setRequestProperty("Accept", "application/json");

            if (con.getResponseCode() == 200) {
                try (var in = new BufferedReader(new InputStreamReader(con.getInputStream()))) {
                    ObjectMapper mapper = new ObjectMapper();
                    JsonNode root = mapper.readTree(in);
                    String path = root.path("pathOrUrl").asText(null);

                    if (path != null && !path.isBlank()) {
                        updateCharacter(path);
                        System.out.println("[CharacterPane] 생성 이미지 적용됨");
                    }
                }
            } else {
                System.out.println("[CharacterPane] 최신 생성 캐릭터 없음 → 기본 유지");
            }
        } catch (Exception e) {
            System.err.println("[CharacterPane] 캐릭터 로딩 실패: " + e.getMessage());
        }
    }



    @FXML
    private void onCharacterClicked(MouseEvent e) {
        if (overlayHost != null) {
            overlayHost.openOverlay("/fxml/FriendList/MyInfoPanel.fxml");
        } else {
            System.err.println("[CharacterPane] overlayHost is null!");
        }
    }

}

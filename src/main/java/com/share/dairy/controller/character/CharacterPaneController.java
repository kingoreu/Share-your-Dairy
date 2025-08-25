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
    }


    @FXML
    public void initialize() {
        UserSession user = UserSession.get();
        if (user == null) return;

        // 1) 기본 캐릭터 (enum 기반)
        var type = user.getCharacterType();
        var defaultPath = type.getImagePath();
        var is = getClass().getResourceAsStream(defaultPath);
        if (is != null) {
            characterImg.setImage(new Image(is));
        }

        // 2) DB에서 최신 생성 캐릭터 있으면 덮어쓰기
        loadLatestGeneratedCharacter(user.getUserId());
    }


    private void loadLatestGeneratedCharacter(Long userId) {
        try {
            String apiUrl = "http://localhost:8080/api/character_keyword_images/latest?userId=" + userId;
            HttpURLConnection con = (HttpURLConnection) new URL(apiUrl).openConnection();
            con.setRequestProperty("Accept", "application/json");

            if (con.getResponseCode() == 200) {
                try (var in = new BufferedReader(new InputStreamReader(con.getInputStream()))) {
                    ObjectMapper mapper = new ObjectMapper();
                    JsonNode root = mapper.readTree(in);
                    String path = root.path("pathOrUrl").asText(null);

                    if (path != null && !path.isBlank()) {
                        updateCharacter(path);
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

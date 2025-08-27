package com.share.dairy.controller.bookshelf;

import com.share.dairy.api.keyword.KeywordImageClient;
import com.share.dairy.auth.UserSession;
import com.share.dairy.dto.keyword.keywordImage.ResponseDto;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

public class BookshelfController {

    @FXML
    private DatePicker datePicker;

    @FXML
    private GridPane cardGrid;

    private final KeywordImageClient client = new KeywordImageClient();
    private List<ResponseDto> images;
    private int currentPage = 0;
    private final int pageSize = 4;

    @FXML
    public void initialize() {
        try {
            long userId = UserSession.requireId();

            images = client.fetchByUserId(userId);  // 서버에서 전체 이미지 리스트 받음
            System.out.println("불러온 이미지 개수 = " + images.size());
            // 첫 페이지
            showPage(currentPage);                  // 첫 페이지 보여주기
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void showPage(int pageIndex) {
        // 1. 페이지 범위 계산
        int start = pageIndex * pageSize;
        int end = Math.min(start + pageSize, images.size());

        // 2. gridpane 초기화
        cardGrid.getChildren().clear();

        // 3. 데이터 슬라이스 후 카드 생성
        List<ResponseDto> subList = images.subList(start, end);

        for (int i = 0; i < subList.size(); i++) {
            ResponseDto dto = subList.get(i);

            // 날짜 붙이기
            Label dateLabel = new Label(dto.getCreatedAt().toLocalDate().toString());
            dateLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: #333;");

            // 경로 처리
            String path = dto.getPathOrUrl();
            if (path.startsWith("/media/")) {
                path = "http://localhost:8080" + path;
            } else {
                path = "file:" + path;
            }

            // 이미지
            ImageView imageView = new ImageView(new Image(path, true));
            imageView.setFitWidth(180);
            imageView.setFitHeight(180);
            imageView.setPreserveRatio(true);

            // 카드
            VBox card = new VBox(20, dateLabel, imageView);
            card.getStyleClass().add("card");

            VBox.setVgrow(imageView, javafx.scene.layout.Priority.ALWAYS);

            // 4. 위치 계산 (2X2로 함)
            int row = i / 2;
            int col = i % 2;

            // 5. GridPane에 추가
            cardGrid.add(card, col, row);
        }
    }

    @FXML
    private void goHome(ActionEvent event) throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/mainFrame/Main.fxml"));
        Parent loginRoot = loader.load();

        Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
        stage.getScene().setRoot(loginRoot);
    }

    @FXML
    private void filterDate(MouseEvent event) {
        LocalDate selectedDate = datePicker.getValue();
        if (selectedDate != null) {
            System.out.println("선택된 날짜: " + selectedDate);
            // TODO: 선택한 날짜 기준으로 이미지 필터링 로직 추가 예정
        }
    }

    @FXML
    private void showPrevPage() {
        if (currentPage > 0) {
            currentPage--;
            showPage(currentPage);
        }
    }

    @FXML
    private void showNextPage() {
        if ((currentPage + 1) * pageSize < images.size()) {
            currentPage++;
            showPage(currentPage);
        }
    }
}
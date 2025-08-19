package com.share.dairy.controller;

import com.share.dairy.repo.DiaryImageRepository;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Collections;
import java.util.Map;

public class CalendarController extends OverlayChildController {

    // FXML 바인딩
    @FXML private Button prevBtn, nextBtn;
    @FXML private Label monthLabel;
    @FXML private GridPane calendarGrid;

    // 상태
    private YearMonth currentYm = YearMonth.now();
    private DiaryImageRepository repo;
    private Image PLACEHOLDER;

    private long userId = 1L; // TODO: 로그인 사용자 id 연동

    @FXML
    public void initialize() {
        // 리포지토리 & 플레이스홀더
        this.repo = new DiaryImageRepository();
        var ph = getClass().getResource("/icons/placeholder.png");
        if (ph == null) throw new IllegalStateException("icons/placeholder.png 누락");
        this.PLACEHOLDER = new Image(ph.toExternalForm());

        // 디버그용 그리드 라인(원하면 주석 처리)
         calendarGrid.setGridLinesVisible(true);

        // 버튼 핸들러
        prevBtn.setOnAction(e -> { currentYm = currentYm.minusMonths(1); refresh(); });
        nextBtn.setOnAction(e -> { currentYm = currentYm.plusMonths(1);  refresh(); });

        // 최초 렌더
        refresh();
    }

    /** 달력 다시 그리기 */
    private void refresh() {
        monthLabel.setText(currentYm.getYear() + " . " + String.format("%02d", currentYm.getMonthValue()));

        calendarGrid.getChildren().clear();
        // (헤더/셀을 꽉 채우도록 grow)
        GridPane.setHgrow(calendarGrid, Priority.ALWAYS);
        GridPane.setVgrow(calendarGrid, Priority.ALWAYS);

        // 1) 요일 헤더
        String[] days = {"SUN","MON","TUE","WED","THU","FRI","SAT"};
        for (int c = 0; c < 7; c++) {
            Label head = new Label(days[c]);
            head.setAlignment(Pos.CENTER);
            head.setMaxWidth(Double.MAX_VALUE);
            head.setStyle("-fx-font-weight: bold;");
            GridPane.setHgrow(head, Priority.ALWAYS);
            calendarGrid.add(head, c, 0);
        }

        // 2) DB에서 이번 달 이미지 맵 로드(실패해도 렌더는 계속)
        Map<LocalDate, String> imageByDate = Collections.emptyMap();
        try {
            LocalDate first = currentYm.atDay(1);
            LocalDate last  = currentYm.atEndOfMonth();
            imageByDate = repo.findKeywordImages(userId, first, last);
        } catch (Exception ex) {
            ex.printStackTrace(); // 로그만 남기고 진행
        }

        // 3) 셀 채우기
        LocalDate first = currentYm.atDay(1);
        int startCol = (first.getDayOfWeek().getValue() % 7); // Sun=0
        int row = 1, col = startCol;

        for (int day = 1; day <= currentYm.lengthOfMonth(); day++) {
            LocalDate date = currentYm.atDay(day);
            String url = imageByDate.getOrDefault(date, null);

            VBox cell = buildDayCell(day, url);
            // 보이도록 최소/선호 크기 부여
            cell.setMinSize(100, 90);
            cell.setPrefSize(120, 110);

            calendarGrid.add(cell, col, row);

            col++;
            if (col > 6) { col = 0; row++; }
        }

        calendarGrid.applyCss();
        calendarGrid.requestLayout();
    }

    /** 날짜 셀 생성 */
    private VBox buildDayCell(int day, String imageUrl) {
        Label dayLabel = new Label(String.valueOf(day));
        dayLabel.setStyle("-fx-font-weight: bold;");

        ImageView iv = new ImageView();
        iv.setFitWidth(48);
        iv.setFitHeight(48);
        iv.setPreserveRatio(true);

        if (imageUrl != null && !imageUrl.isBlank()) {
            iv.setImage(new Image(imageUrl, 48, 48, true, true)); // background loading
        } else {
            iv.setImage(PLACEHOLDER);
        }

        VBox box = new VBox(dayLabel, iv);
        box.setAlignment(Pos.TOP_CENTER);
        box.setPadding(new Insets(6));
        box.setStyle("-fx-background-color: white; -fx-border-color: #ddd; -fx-background-radius: 8; -fx-border-radius: 8;");
        return box;
    }
}

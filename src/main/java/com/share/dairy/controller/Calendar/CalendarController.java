package com.share.dairy.controller.Calendar;

import com.share.dairy.controller.OverlayChildController;
import com.share.dairy.repo.DiaryImageRepository;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.RowConstraints;
import javafx.scene.layout.VBox;
import java.net.URL;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Collections;
import java.util.Map;
import java.util.ResourceBundle;

public class CalendarController extends OverlayChildController {

    // FXML 바인딩
    @FXML private Button prevBtn, nextBtn;
    @FXML private Label monthLabel;
    @FXML private GridPane calendarGrid;

    // 상태
    private YearMonth currentYm;       // 현재 보이는 달
    private DiaryImageRepository repo;
    private Image PLACEHOLDER;


    @FXML
    @Override
    public void initialize(URL url, ResourceBundle rb) {
        // 1) 기본은 현재 달
        currentYm = YearMonth.now();

        // 2) 리포지토리/플레이스홀더 준비
        repo = new DiaryImageRepository();
        var ph = getClass().getResource("/icons/placeholder.png");
        if (ph == null) throw new IllegalStateException("icons/placeholder.png 누락");
        PLACEHOLDER = new Image(ph.toExternalForm());

        // (디버그용) 그리드 라인 보이기
         calendarGrid.setGridLinesVisible(true);

        // 3) 좌/우 버튼으로 월 이동
        prevBtn.setOnAction(e -> moveMonth(-1));
        nextBtn.setOnAction(e -> moveMonth(+1));

        // 4) 첫 렌더
        refresh();
    }

        // CalendarController 필드
        private static final String MEDIA_BASE_URL = "http://localhost:8080"; // 서버 주소에 맞춰 수정

        private String toLoadableUrl(String pathOrUrl) {
            if (pathOrUrl == null || pathOrUrl.isBlank()) return null;
            String p = pathOrUrl.trim();

            // 이미 절대 URL이면 그대로 사용
            if (p.startsWith("http://") || p.startsWith("https://") || p.startsWith("file:")) return p;

            // /media/.. 형태면 서버 베이스 붙이기
            if (p.startsWith("/")) return MEDIA_BASE_URL + p;

            // 로컬 파일 경로라면 file:///로 변환
            return java.nio.file.Paths.get(p).toUri().toString(); // => file:///C:/... 또는 file:///home/...
        }


    /** 월 이동 공통 처리 */
    private void moveMonth(int deltaMonth) {
        currentYm = currentYm.plusMonths(deltaMonth);
        refresh();
    }
    
    /** 열/행 제약을 매번 보강해서 레이아웃이 0으로 접히는 걸 방지 */
    private void ensureGridLayout() {
        if (calendarGrid.getColumnConstraints().isEmpty()) {
            for (int i = 0; i < 7; i++) {
                ColumnConstraints cc = new ColumnConstraints();
                cc.setPercentWidth(100.0 / 7.0);
                calendarGrid.getColumnConstraints().add(cc);
            }
        }
        if (calendarGrid.getRowConstraints().isEmpty()) {
            // 헤더 1 + 날짜 6 = 7행 (필요하면 6행만 써도 OK)
            for (int r = 0; r < 7; r++) {
                RowConstraints rc = new RowConstraints();
                rc.setVgrow(Priority.ALWAYS);
                calendarGrid.getRowConstraints().add(rc);
            }
        }
    }

    /** 달력 다시 그리기 */
    private void refresh() {
        // 상단 라벨 (YYYY . MM)
        monthLabel.setText(currentYm.getYear() + " . " + String.format("%02d", currentYm.getMonthValue()));

        // 레이아웃 초기화
        calendarGrid.getChildren().clear();
        GridPane.setHgrow(calendarGrid, Priority.ALWAYS);
        GridPane.setVgrow(calendarGrid, Priority.ALWAYS);

        // ✅ 레이아웃 보강
        ensureGridLayout();

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

        // 2) 이번 달 범위
        LocalDate first = currentYm.atDay(1);
        LocalDate last  = currentYm.atEndOfMonth();

        // 3) DB에서 날짜→이미지 URL 맵 조회 (없어도 렌더 계속)
        Map<LocalDate, String> imageByDate = Collections.emptyMap();
        try {
            Long uid = com.share.dairy.auth.UserSession.currentId();
            imageByDate = repo.findKeywordImages(uid, first, last);
            if (imageByDate == null) imageByDate = Collections.emptyMap();
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        // 4) 날짜 셀 생성
        int startCol = (first.getDayOfWeek().getValue() % 7); // Sun=0
        int row = 1, col = startCol;

        for (int day = 1; day <= currentYm.lengthOfMonth(); day++) {
            LocalDate date = currentYm.atDay(day);
            String url = imageByDate.get(date);

            VBox cell = buildDayCell(day, url, date);
            cell.setMinSize(100, 80);
            cell.setPrefSize(120, 100);

            calendarGrid.add(cell, col, row);
            col++;
            if (col > 6) { col = 0; row++; }
        }

        calendarGrid.applyCss();
        calendarGrid.requestLayout();
         // 🔎 확인 로그
        System.out.println("[Calendar] children=" + calendarGrid.getChildren().size()
                + ", cols=" + calendarGrid.getColumnConstraints().size()
                + ", rows=" + calendarGrid.getRowConstraints().size());
    }

    /** 날짜 셀 생성 */
    private VBox buildDayCell(int day, String imageUrl, LocalDate date) {
    Label dayLabel = new Label(String.valueOf(day));
    dayLabel.setStyle("-fx-font-weight: bold;");

    ImageView iv = new ImageView();
    iv.setFitWidth(56);
    iv.setFitHeight(56);
    iv.setPreserveRatio(true);
    iv.setSmooth(true);

    String loadable = toLoadableUrl(imageUrl);
    if (loadable != null) {
        Image img = new Image(loadable, 56, 56, true, true, true); // backgroundLoading=true
        iv.setImage(img);
        img.errorProperty().addListener((obs, wasErr, isErr) -> {
            if (isErr) iv.setImage(PLACEHOLDER);
        });
    } else {
        iv.setImage(PLACEHOLDER);
    }

    VBox box = new VBox(dayLabel, iv);
    box.setAlignment(Pos.TOP_CENTER);
    box.setPadding(new Insets(6));
    box.setStyle("-fx-background-color: white; -fx-border-color: #ddd; -fx-background-radius: 7; -fx-border-radius: 7;");
    if (date.equals(LocalDate.now())) {
        box.setStyle("-fx-background-color: white; -fx-border-color: #f48cab; -fx-border-width: 2; -fx-background-radius: 8; -fx-border-radius: 8;");
    }
    return box;
}
}

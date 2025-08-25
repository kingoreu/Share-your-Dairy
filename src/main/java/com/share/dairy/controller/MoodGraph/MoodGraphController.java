package com.share.dairy.controller.MoodGraph;

import javafx.application.Platform;
import javafx.fxml.FXML;

import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;

import java.net.URL;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.ResourceBundle;

import com.share.dairy.repo.MoodRepository;
import com.share.dairy.controller.OverlayChildController;
import com.share.dairy.model.mood.MoodPoint;

//diary analysis 테이블 user_id 추가 및 user_id로 mood 조회
public class MoodGraphController extends OverlayChildController{

    @FXML private ToggleButton weekToggle;
    @FXML private ToggleButton days15Toggle;
    @FXML private ToggleButton monthToggle;

    @FXML private ToggleGroup  rangeGroup;          // FXML의 fx:id="rangeGroup" 주입
    @FXML private LineChart<String, Number> moodChart;

    private final MoodRepository repo = new MoodRepository();
    private long userId = 1L;                       // 로그인 값으로 교체

    private enum Range { WEEK(7), DAYS15(15), MONTH(30); final int days; Range(int d){ this.days=d; } }
    private static final DateTimeFormatter LABEL_FMT = DateTimeFormatter.ofPattern("MM-dd");

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // 차트 기본 스타일
        moodChart.setCreateSymbols(false);   // 기본은 선-only
        moodChart.setAnimated(false);

        // Y축 1~10 고정
        if (moodChart.getYAxis() instanceof NumberAxis yAxis) {
            yAxis.setAutoRanging(false);
            yAxis.setLowerBound(1);
            yAxis.setUpperBound(10);
            yAxis.setTickUnit(1);
            yAxis.setMinorTickVisible(false);
        }

        // 토글 변경 시 DB 로드
        rangeGroup.selectedToggleProperty().addListener((obs, oldT, newT) -> {
            if (newT == null) return;
            if (newT == weekToggle)        loadDays(Range.WEEK);
            else if (newT == days15Toggle) loadDays(Range.DAYS15);
            else if (newT == monthToggle)  loadDays(Range.MONTH);
        });

        // 기본: 1주
        weekToggle.setSelected(true);
        loadDays(Range.WEEK);
    }

    /* FXML onAction 훅 (버튼 클릭 시 직접 호출되어도 OK) */
    @FXML private void loadWeekData()   { loadDays(Range.WEEK); }
    @FXML private void load15DaysData() { loadDays(Range.DAYS15); }
    @FXML private void loadMonthData()  { loadDays(Range.MONTH); }

    /** 공통 로더: DB에서 일별 평균 행복도 조회 후 그리기 */
    private void loadDays(Range range) {
        LocalDate toExclusive = LocalDate.now().plusDays(1); // 오늘 포함하려고 +1 (exclusive)
        LocalDate from        = toExclusive.minusDays(range.days);

        new Thread(() -> {
            try {
                List<MoodPoint> rows = repo.findDailyMood(userId, from, toExclusive);
                Map<LocalDate, Integer> dayToScore = new HashMap<>();
                for (MoodPoint mp : rows) dayToScore.put(mp.date(), mp.score());

                // 1) 기본 데이터 채우기
                List<String> labels = new ArrayList<>(range.days);
                List<Number> values = new ArrayList<>(range.days);
                for (int i = 0; i < range.days; i++) {
                    LocalDate d = from.plusDays(i);
                    labels.add(LABEL_FMT.format(d));
                    Integer s = dayToScore.get(d);
                    values.add(s != null ? s : Double.NaN); // 데이터 없는 날은 끊김
                }

                // 2) 유효 포인트( NaN 제외 ) 개수
                long valid = values.stream()
                        .filter(v -> v != null && !Double.isNaN(v.doubleValue()))
                        .count();

                // 3) 포인트가 1개면 좌우에 더미(값 NaN) 추가 → 중앙 배치
                if (valid == 1) {
                    labels.add(0, " ");            values.add(0, Double.NaN);  // 왼쪽 더미
                    labels.add("  ");              values.add(Double.NaN);     // 오른쪽 더미
                }

                // 4) 차트 갱신
                updateChart(labels, values);
            } catch (Exception e) {
                e.printStackTrace();
                updateChart(List.of(), List.of());
            }
        }, "mood-load-thread").start();
    }

    /** 차트 갱신(UI 스레드 보장) + 포인트 적을 때 점 크게 */
    private void updateChart(List<String> labels, List<Number> values) {
        Runnable ui = () -> {
            moodChart.getData().clear();

            XYChart.Series<String, Number> series = new XYChart.Series<>();
            for (int i = 0; i < labels.size(); i++) {
                series.getData().add(new XYChart.Data<>(labels.get(i), values.get(i)));
            }
            moodChart.getData().add(series);

            // 유효 포인트 개수
            long valid = values.stream()
                    .filter(v -> v != null && !Double.isNaN(v.doubleValue()))
                    .count();

            boolean fewPoints = valid <= 2;      // 1~2개면 점 보이게 + 크게
            moodChart.setCreateSymbols(fewPoints);

            Platform.runLater(() -> {
                if (series.getNode() != null) {
                    series.getNode().setStyle("-fx-stroke-width: 2.5px;"); // 선 두께 살짝 업
                }
                if (fewPoints) {
                    for (XYChart.Data<String, Number> d : series.getData()) {
                        Number y = d.getYValue();
                        if (y == null || Double.isNaN(y.doubleValue())) continue; // 더미 제외
                        if (d.getNode() != null) {
                            d.getNode().setStyle("-fx-background-radius: 9px; -fx-padding: 9px;");
                        } else {
                            d.nodeProperty().addListener((o, oldN, node) -> {
                                if (node != null) node.setStyle("-fx-background-radius: 9px; -fx-padding: 9px;");
                            });
                        }
                    }
                }
            });
        };
        if (Platform.isFxApplicationThread()) ui.run(); else Platform.runLater(ui);
    }

    // 로그인 연동 시 사용
    public void setUserId(long userId) { this.userId = userId; }
}

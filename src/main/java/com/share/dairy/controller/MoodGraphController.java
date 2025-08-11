package com.share.dairy.controller;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;

import java.net.URL;
import java.util.ResourceBundle;

public class MoodGraphController implements Initializable {

    @FXML private ToggleButton weekToggle;
    @FXML private ToggleButton days15Toggle;
    @FXML private ToggleButton monthToggle;

    @FXML private LineChart<String, Number> moodChart;

    private ToggleGroup rangeGroup;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // 토글 그룹 구성 (단일 선택)
        rangeGroup = new ToggleGroup();
        weekToggle.setToggleGroup(rangeGroup);
        days15Toggle.setToggleGroup(rangeGroup);
        monthToggle.setToggleGroup(rangeGroup);

        // 차트 스타일: 시안처럼 선만 보이게
        moodChart.setCreateSymbols(false);  // 점 제거
        moodChart.setAnimated(false);       // 갱신 애니메이션 제거(선호에 따라)

        // 선택 변경 시 자동 데이터 로드
        rangeGroup.selectedToggleProperty().addListener((obs, oldT, newT) -> {
            if (newT == null) return; // 아무 것도 선택 안 된 케이스 방지
            if (newT == weekToggle) {
                loadWeekData();
            } else if (newT == days15Toggle) {
                load15DaysData();
            } else if (newT == monthToggle) {
                loadMonthData();
            }
        });

        // 기본 선택: 1week
        weekToggle.setSelected(true); // → 리스너가 loadWeekData() 호출
    }

    /* === 데이터 로더들 === */
    @FXML
    private void loadWeekData() {
        String[] dates = new String[7];
        int[] scores = new int[7];
        for (int i = 0; i < 7; i++) {
            dates[i] = (i + 1) + "일";
            scores[i] = 2 + (int) (Math.random() * 4); // 2~5
        }
        updateChart(dates, scores);
    }

    @FXML
    private void load15DaysData() {
        String[] dates = new String[15];
        int[] scores = new int[15];
        for (int i = 0; i < 15; i++) {
            dates[i] = (i + 1) + "일";
            scores[i] = 2 + (int) (Math.random() * 4);
        }
        updateChart(dates, scores);
    }

    @FXML
    private void loadMonthData() {
        String[] dates = new String[30];
        int[] scores = new int[30];
        for (int i = 0; i < 30; i++) {
            dates[i] = (i + 1) + "일";
            scores[i] = 2 + (int) (Math.random() * 4);
        }
        updateChart(dates, scores);
    }

    /* === 차트 갱신 === */
    private void updateChart(String[] dates, int[] scores) {
        moodChart.getData().clear();

        XYChart.Series<String, Number> series = new XYChart.Series<>();
        for (int i = 0; i < dates.length; i++) {
            series.getData().add(new XYChart.Data<>(dates[i], scores[i]));
        }

        // CSS로 더 예쁘게 잡고 싶다면 커스텀 클래스 추가 (선택)
        // series.getNode().getStyleClass().add("mood-series");

        moodChart.getData().add(series);
    }
}

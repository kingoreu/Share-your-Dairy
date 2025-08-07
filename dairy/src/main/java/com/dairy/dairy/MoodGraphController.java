package com.dairy.dairy;

import javafx.fxml.FXML;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.XYChart;

public class MoodGraphController {

    @FXML
    private LineChart<Number, Number> moodChart;

    @FXML
    public void initialize() {
        loadWeekData(); // 기본으로 1주 표시
    }

    @FXML
    private void loadWeekData() {
        updateChart(new int[]{3, 4, 2, 5, 4, 3, 4});
    }

    @FXML
    private void load15DaysData() {
        updateChart(new int[]{3, 4, 2, 5, 4, 3, 4, 5, 2, 3, 4, 4, 3, 2, 5});
    }

    @FXML
    private void loadMonthData() {
        int[] data = new int[30];
        for (int i = 0; i < 30; i++) data[i] = 2 + (int)(Math.random() * 4); // 2~5 랜덤
        updateChart(data);
    }

    private void updateChart(int[] moodScores) {
        moodChart.getData().clear();

        XYChart.Series<Number, Number> series = new XYChart.Series<>();
        for (int i = 0; i < moodScores.length; i++) {
            series.getData().add(new XYChart.Data<>(i + 1, moodScores[i]));
        }

        moodChart.getData().add(series);
    }
}

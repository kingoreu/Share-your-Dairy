package com.dairy.dairy;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;

import java.net.URL;
import java.util.ResourceBundle;

public class MoodGraphController implements Initializable {

    @FXML private ToggleButton weekToggle;
    @FXML private ToggleButton days15Toggle;
    @FXML private ToggleButton monthToggle;

    @FXML private LineChart<String, Number> moodChart;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        ToggleGroup group = new ToggleGroup();
        weekToggle.setToggleGroup(group);
        days15Toggle.setToggleGroup(group);
        monthToggle.setToggleGroup(group);
    }

    @FXML
    private void loadWeekData() {
        String[] dates = new String[7];
        int[] scores = new int[7];
        for (int i = 0; i < 7; i++) {
            dates[i] =  (i + 1) + "일";
            scores[i] = 2 + (int)(Math.random() * 4); // 2~5
        }
        updateChart(dates, scores);
    }

    @FXML
    private void load15DaysData() {
        String[] dates = new String[15];
        int[] scores = new int[15];
        for (int i = 0; i < 15; i++) {
            dates[i] =  (i + 1) + "일";
            scores[i] = 2 + (int)(Math.random() * 4); // 2~5
        }
        updateChart(dates, scores);
    }

    @FXML
    private void loadMonthData() {
        String[] dates = new String[30];
        int[] scores = new int[30];
        for (int i = 0; i < 30; i++) {
            dates[i] =   (i + 1) + "일";
            scores[i] = 2 + (int)(Math.random() * 4);
        }
        updateChart(dates, scores);
    }

    private void updateChart(String[] dates, int[] scores) {
        moodChart.getData().clear();

        XYChart.Series<String, Number> series = new XYChart.Series<>();
        for (int i = 0; i < dates.length; i++) {
            series.getData().add(new XYChart.Data<>(dates[i], scores[i]));
        }

        moodChart.getData().add(series);
    }
}

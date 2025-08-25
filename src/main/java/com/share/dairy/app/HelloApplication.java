package com.share.dairy.app;

import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import com.share.dairy.ServerApplication;
import javafx.application.Platform;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.text.Font;
import javafx.stage.Stage;
import java.util.Map;

public class HelloApplication extends Application {

    private ConfigurableApplicationContext springContext;

    @Override
    public void init() {
        // Spring Boot 서버 기동 (별도 스레드)
        Thread serverThread = new Thread(() -> {
            springContext = new SpringApplicationBuilder(ServerApplication.class)
                    .properties(Map.of(
                            "server.port", "8080",
                            "spring.datasource.url", "jdbc:mysql://113.198.238.119:3306/dairy?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Seoul&characterEncoding=UTF-8",
                            "spring.datasource.username", "root",
                            "spring.datasource.password", "sohyun"
                    ))
                    .run();
        });
        serverThread.setDaemon(true);
        serverThread.start();
    }

    @Override
    public void start(Stage stage) throws Exception {
        Router.init(stage);

        // 폰트 등록
        Font.loadFont(
                getClass().getResourceAsStream("/fonts/NanumSquareRoundR.ttf"),
                14 // 기본 크기, 실제 표시할 때는 CSS에서 조절됨
        );

        Font.loadFont(
                getClass().getResourceAsStream("/fonts/NanumSquareRoundB.ttf"),
                14 // 기본 크기, 실제 표시할 때는 CSS에서 조절됨
        );

        Font.loadFont(
                getClass().getResourceAsStream("/fonts/NanumSquareRoundEB.ttf"),
                14 // 기본 크기, 실제 표시할 때는 CSS에서 조절됨
        );

        Font.loadFont(
                getClass().getResourceAsStream("/fonts/DungGeunMo.ttf"),
                14 // 기본 크기, 실제 표시할 때는 CSS에서 조절됨
        );

        Parent root = FXMLLoader.load(getClass().getResource("/fxml/login/Login.fxml"));
        Scene scene = new Scene(root, 800, 600);
        stage.setTitle("공유일기");
        stage.setScene(scene);
        stage.show();

    }

    @Override
    public void stop() {
        if (springContext != null) {
            springContext.close();
        }
        Platform.exit();
    }

    public static void main(String[] args) {
        launch();
    }
}

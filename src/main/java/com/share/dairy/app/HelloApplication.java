package com.share.dairy.app;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class HelloApplication extends Application {

    private URL resolveFXML(String fileName) {
        // 1) 클래스패스에서 흔한 위치들 먼저 탐색
        for (String cpPath : List.of(
                "/" + fileName,
                "/com/share/dairy/app/" + fileName,
                "/com/share/dairy/" + fileName,
                "/fxml/" + fileName,
                "/view/" + fileName
        )) {
            URL u = HelloApplication.class.getResource(cpPath);
            if (u != null) {
                System.out.println("[FXML] found on classpath: " + cpPath);
                return u;
            }
        }

        // 2) 파일 시스템에서 자주 쓰는 위치들 탐색 (프로젝트 루트 기준)
        Path base = Paths.get(System.getProperty("user.dir")); // 예: C:\Java\dairy
        for (Path p : List.of(
                base.resolve("src/main/java/" + fileName),
                base.resolve("src/main/java/com/share/dairy/app/" + fileName),
                base.resolve("src/main/java/com/share/dairy/" + fileName),
                base.resolve("src/main/resources/" + fileName),
                base.resolve("src/main/resources/com/share/dairy/app/" + fileName),
                base.resolve(fileName) // 루트 바로 아래
        )) {
            if (Files.exists(p)) {
                try {
                    System.out.println("[FXML] found on filesystem: " + p);
                    return p.toUri().toURL();
                } catch (Exception ignored) {}
            }
        }
        return null;
    }

    @Override
    public void start(Stage stage) throws IOException {
        URL fxmlUrl = resolveFXML("hello-view.fxml");
        if (fxmlUrl == null) {
            throw new IllegalStateException("Cannot locate hello-view.fxml (classpath or filesystem).");
        }
        FXMLLoader loader = new FXMLLoader(fxmlUrl);
        Scene scene = new Scene(loader.load(), 320, 240);
        stage.setTitle("Hello!");
        stage.setScene(scene);
        stage.show();
    }
}

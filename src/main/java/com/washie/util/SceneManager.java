package com.washie.util;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;

@Component
public class SceneManager {

    private final ApplicationContext context;
    private Stage primaryStage;

    public SceneManager(ApplicationContext context) {
        this.context = context;
    }

    public void setPrimaryStage(Stage stage) {
        this.primaryStage = stage;
    }

    public Stage getPrimaryStage() {
        return primaryStage;
    }

    public void switchTo(String fxmlPath) throws IOException {
        if (primaryStage == null) {
            throw new IllegalStateException("Primary stage belum diset!");
        }
        Parent root = loadFxml(fxmlPath);
        Scene scene = primaryStage.getScene();
        if (scene == null) {
            primaryStage.setScene(new Scene(root, 1100, 750));
        } else {
            scene.setRoot(root);
        }
        primaryStage.show();
    }

    private Parent loadFxml(String fxmlPath) throws IOException {
        FXMLLoader loader = new FXMLLoader();
        loader.setControllerFactory(context::getBean);

        InputStream is = getClass().getResourceAsStream(fxmlPath);
        if (is == null) {
            throw new IOException("FXML tidak ditemukan: " + fxmlPath);
        }

        java.net.URL location = getClass().getResource(fxmlPath);
        loader.setLocation(location);

        return loader.load(is);
    }
}
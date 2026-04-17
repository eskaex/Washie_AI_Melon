package com.washie;

import com.washie.util.SceneManager;
import javafx.application.Application;
import javafx.stage.Stage;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

@SpringBootApplication
public class MainApp extends Application {

    private static ConfigurableApplicationContext springContext;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void init() {
        springContext = SpringApplication.run(MainApp.class);
    }

    @Override
    public void start(Stage primaryStage) throws Exception {
        SceneManager sceneManager = springContext.getBean(SceneManager.class);
        sceneManager.setPrimaryStage(primaryStage);

        primaryStage.setTitle("Washie - Virtual Assistant Laundry");
        primaryStage.setMinWidth(900);
        primaryStage.setMinHeight(650);

        // Load login lewat SceneManager langsung
        sceneManager.switchTo("/com/washie/view/LoginView.fxml");
        primaryStage.show();
    }

    @Override
    public void stop() {
        springContext.close();
    }
}
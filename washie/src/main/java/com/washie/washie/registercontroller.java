package com.washie.washie;

import javafx.fxml.FXML;
import javafx.scene.control.Label;

public class registercontroller {
    @FXML
    private Label welcomeText;

    //test
    @FXML
    protected void onHelloButtonClick() {
        welcomeText.setText("Welcome to JavaFX Application!");
    }
}

package com.washie.util;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;

@Component
public class SpringFXMLLoader {

    private final ApplicationContext context;

    public SpringFXMLLoader(ApplicationContext context) {
        this.context = context;
    }

    public Parent load(String fxmlPath) throws IOException {
        FXMLLoader loader = new FXMLLoader();
        loader.setControllerFactory(context::getBean);

        // Pakai getResourceAsStream — tidak ada URL encoding issue
        InputStream is = getClass().getResourceAsStream(fxmlPath);
        if (is == null) {
            throw new IOException("FXML tidak ditemukan: " + fxmlPath);
        }

        // Set location untuk resolve relative paths di FXML (misal @/css/styles.css)
        loader.setLocation(getClass().getResource(fxmlPath));
        return loader.load(is);
    }
}
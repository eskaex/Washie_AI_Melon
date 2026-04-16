package com.washie.controller;

import com.washie.service.UserService;
import com.washie.util.SceneManager;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import org.springframework.stereotype.Controller;

public class RegisterController implements Initializable {
    @FXML
    TextField tfNama;
    @FXML
    TextField tfNoTelp;
    @FXML
    PasswordField pfPassword;
    @FXML
    TextField tfPasswordVisible;
    @FXML
    PasswordField pfKonfirmasi;
    @FXML
    TextField tfKonfirmasiVisible;
    @FXML
    Button btnTogglePass;
    @FXML
    Button btnToggleKonfirmasi;
    @FXML
    Button btnDaftar;
    @FXML
    Hyperlink hlLogin;
    @FXML
    Label lblError;
    @FXML
    Label lblSuccess;

    private boolean passVisible = false;
    private boolean konfirmasiVisible = false;

    private final UserService userService;
    private final SceneManager sceneManager;

    public RegisterController(UserService userService, SceneManager sceneManager) {
        this.userService = userService;
        this.sceneManager = sceneManager;
    }

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        tfPasswordVisible.setVisible(false);
        tfPasswordVisible.setManaged(false);
        tfKonfirmasiVisible.setVisible(false);
        tfKonfirmasiVisible.setManaged(false);
        lblError.setVisible(false);
        lblSuccess.setVisible(false);
    }

    @FXML
    private void handleTogglePass() {
        passVisible = !passVisible;
        toggleField(pfPassword, tfPasswordVisible, passVisible);
        btnTogglePass.setText(passVisible ? "🔒" : "👁");
    }

    @FXML
    private void handleToggleKonfirmasi() {
        konfirmasiVisible = !konfirmasiVisible;
        toggleField(pfKonfirmasi, tfKonfirmasiVisible, konfirmasiVisible);
        btnToggleKonfirmasi.setText(konfirmasiVisible ? "🔒" : "👁");
    }

    private void toggleField(PasswordField pf, TextField tf, boolean show) {
        if (show) {
            tf.setText(pf.getText());
            pf.setVisible(false); pf.setManaged(false);
            tf.setVisible(true);  tf.setManaged(true);
        } else {
            pf.setText(tf.getText());
            tf.setVisible(false); tf.setManaged(false);
            pf.setVisible(true);  pf.setManaged(true);
        }
    }

    @FXML
    private void handleDaftar() {
        String nama   = tfNama.getText().trim();
        String noTelp = tfNoTelp.getText().trim();
        String pass   = passVisible ? tfPasswordVisible.getText() : pfPassword.getText();
        String konfir = konfirmasiVisible ? tfKonfirmasiVisible.getText() : pfKonfirmasi.getText();

        lblError.setVisible(false);
        lblSuccess.setVisible(false);

        if (nama.isEmpty()) { showError("Nama lengkap tidak boleh kosong."); return; }
        if (pass.length() < 6) { showError("Password minimal 6 karakter."); return; }
        if (!pass.equals(konfir)) { showError("Konfirmasi password tidak cocok."); return; }

        try {
            userService.register(nama, noTelp.isEmpty() ? null : noTelp, pass);
            lblSuccess.setText("Akun berhasil dibuat! Silahkan login.");
            lblSuccess.setVisible(true);
            tfNama.clear(); tfNoTelp.clear(); pfPassword.clear(); pfKonfirmasi.clear();
        } catch (IllegalArgumentException e) {
            showError(e.getMessage());
        }
    }

    @FXML
    private void handleGoLogin() {
        try {
            sceneManager.switchTo("/com/washie/view/loginview.fxml");
        } catch (Exception e) {
            showError("Gagal membuka halaman login.");
        }
    }

    private void showError(String msg) {
        lblError.setText(msg);
        lblError.setVisible(true);
    }
}
}

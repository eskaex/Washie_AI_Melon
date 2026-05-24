package com.washie.controller;

import com.washie.service.UserService;
import com.washie.util.SceneManager;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import org.springframework.stereotype.Controller;

import java.net.URL;
import java.util.ResourceBundle;

@Controller
public class RegisterController implements Initializable {

    @FXML private TextField tfNama;
    @FXML private TextField tfNoTelp;
    @FXML private PasswordField pfPassword;
    @FXML private TextField tfPasswordVisible;
    @FXML private PasswordField pfKonfirmasi;
    @FXML private TextField tfKonfirmasiVisible;
    @FXML private Button btnTogglePass;
    @FXML private Button btnToggleKonfirmasi;
    @FXML private Label lblError;
    @FXML private Label lblSuccess;

    private boolean passVisible      = false;
    private boolean konfirmasiVisible = false;

    private final UserService  userService;
    private final SceneManager sceneManager;

    public RegisterController(UserService userService, SceneManager sceneManager) {
        this.userService  = userService;
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

    @FXML private void handleTogglePass() {
        passVisible = !passVisible;
        toggle(pfPassword, tfPasswordVisible, passVisible);
        btnTogglePass.setText(passVisible ? "👁" : "🙈");
    }

    @FXML private void handleToggleKonfirmasi() {
        konfirmasiVisible = !konfirmasiVisible;
        toggle(pfKonfirmasi, tfKonfirmasiVisible, konfirmasiVisible);
        btnToggleKonfirmasi.setText(konfirmasiVisible ? "👁" : "🙈");
    }

    private void toggle(PasswordField pf, TextField tf, boolean show) {
        if (show) {
            tf.setText(pf.getText());
            pf.setVisible(false);  pf.setManaged(false);
            tf.setVisible(true);   tf.setManaged(true);
        } else {
            pf.setText(tf.getText());
            tf.setVisible(false);  tf.setManaged(false);
            pf.setVisible(true);   pf.setManaged(true);
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

        if (nama.isEmpty())      { showError("Nama lengkap tidak boleh kosong.");   return; }
        if (noTelp.isEmpty())    { showError("Nomor telepon tidak boleh kosong.");  return; }
        if (!noTelp.matches("^08[0-9]{9,11}$")) {
            showError("Nomor telepon harus diawali '08', berisi angka, dan 11-13 digit.");
            return;
        }

        if (pass.length() < 6)   { showError("Password minimal 6 karakter.");       return; }
        if (!pass.equals(konfir)) { showError("Konfirmasi password tidak cocok.");   return; }

        try {
            userService.register(nama, noTelp, pass);
            tfNama.clear();
            tfNoTelp.clear();

            if (passVisible) {
                tfPasswordVisible.clear();
                pfPassword.clear();
                passVisible = false;
                toggle(pfPassword, tfPasswordVisible, false);
                btnTogglePass.setText("🙈");
            } else {
                pfPassword.clear();
            }

            if (konfirmasiVisible) {
                tfKonfirmasiVisible.clear();
                pfKonfirmasi.clear();
                konfirmasiVisible = false;
                toggle(pfKonfirmasi, tfKonfirmasiVisible, false);
                btnToggleKonfirmasi.setText("🙈");
            } else {
                pfKonfirmasi.clear();
            }

            lblSuccess.setText("Akun berhasil dibuat! Silahkan login.");
            lblSuccess.setVisible(true);

        } catch (IllegalArgumentException e) {
            showError(e.getMessage());
        }
    }

    @FXML
    private void handleGoLogin() {
        try {
            sceneManager.switchTo("/com/washie/view/LoginView.fxml");
        } catch (Exception e) {
            showError("Gagal membuka halaman login.");
        }
    }

    private void showError(String msg) {
        lblError.setText(msg);
        lblError.setVisible(true);
    }
}

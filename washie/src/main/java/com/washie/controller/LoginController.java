package controller;

import com.washie.model.User;
import com.washie.service.UserService;
import com.washie.util.SceneManager;
import com.washie.util.SessionManager;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import org.springframework.stereotype.Controller;

import java.net.URL;
import java.util.Optional;
import java.util.ResourceBundle;

@Controller
public class LoginController implements Initializable {

    @FXML private TextField tfNama;
    @FXML private PasswordField pfPassword;
    @FXML private TextField tfPasswordVisible;
    @FXML private Button btnTogglePassword;
    @FXML private Button btnLogin;
    @FXML private Hyperlink hlDaftar;
    @FXML private Label lblError;

    private boolean passwordVisible = false;

    private final UserService userService;
    private final SessionManager sessionManager;
    private final SceneManager sceneManager;

    public LoginController(UserService userService, SessionManager sessionManager, SceneManager sceneManager) {
        this.userService = userService;
        this.sessionManager = sessionManager;
        this.sceneManager = sceneManager;
    }

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        tfPasswordVisible.setVisible(false);
        tfPasswordVisible.setManaged(false);
        lblError.setVisible(false);

        // Sync password fields
        pfPassword.textProperty().addListener((obs, o, n) -> {
            if (!passwordVisible) tfPasswordVisible.setText(n);
        });
        tfPasswordVisible.textProperty().addListener((obs, o, n) -> {
            if (passwordVisible) pfPassword.setText(n);
        });

        // Enter key triggers login
        tfNama.setOnAction(e -> pfPassword.requestFocus());
        pfPassword.setOnAction(e -> handleLogin());
        tfPasswordVisible.setOnAction(e -> handleLogin());
    }

    @FXML
    private void handleTogglePassword() {
        passwordVisible = !passwordVisible;
        if (passwordVisible) {
            tfPasswordVisible.setText(pfPassword.getText());
            pfPassword.setVisible(false);
            pfPassword.setManaged(false);
            tfPasswordVisible.setVisible(true);
            tfPasswordVisible.setManaged(true);
            btnTogglePassword.setText("👁");
        } else {
            pfPassword.setText(tfPasswordVisible.getText());
            tfPasswordVisible.setVisible(false);
            tfPasswordVisible.setManaged(false);
            pfPassword.setVisible(true);
            pfPassword.setManaged(true);
            btnTogglePassword.setText("🙈");
        }
    }

    @FXML
    private void handleLogin() {
        String nama = tfNama.getText().trim();
        String pass = passwordVisible ? tfPasswordVisible.getText() : pfPassword.getText();

        if (nama.isEmpty() || pass.isEmpty()) {
            showError("Nama lengkap dan password tidak boleh kosong.");
            return;
        }

        Optional<User> user = userService.login(nama, pass);
        if (user.isEmpty()) {
            showError("Nama atau password salah. Silahkan coba lagi.");
            return;
        }

        sessionManager.setCurrentUser(user.get());
        lblError.setVisible(false);

        try {
            if (user.get().getRole() == User.Role.ADMIN) {
                sceneManager.switchTo("/com/washie/view/AdminDashboardView.fxml");
            } else {
                sceneManager.switchTo("/com/washie/view/ChatView.fxml");
            }
        } catch (Exception e) {
            showError("Gagal membuka halaman: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @FXML
    private void handleGoDaftar() {
        try {
            sceneManager.switchTo("/com/washie/view/RegisterView.fxml");
        } catch (Exception e) {
            showError("Gagal membuka halaman registrasi.");
        }
    }

    private void showError(String msg) {
        lblError.setText(msg);
        lblError.setVisible(true);
    }
}

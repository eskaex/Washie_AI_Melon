package com.washie.controller;

import com.washie.model.Pesanan;
import com.washie.service.PesananService;
import com.washie.util.SceneManager;
import com.washie.util.SessionManager;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.paint.Color;
import org.springframework.stereotype.Controller;

import java.net.URL;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.ResourceBundle;
import java.util.stream.Collectors;

@Controller
public class AdminDashboardController implements Initializable {

    @FXML private Label lblAdminName;
    @FXML private Label lblDiproses;
    @FXML private Label lblAktif;
    @FXML private Label lblDiambil;
    @FXML private TextField tfCari;

    @FXML private TableView<Pesanan> tablePesanan;
    @FXML private TableColumn<Pesanan, String> colId;
    @FXML private TableColumn<Pesanan, String> colPengguna;
    @FXML private TableColumn<Pesanan, String> colLayanan;
    @FXML private TableColumn<Pesanan, LocalDate> colTanggal;
    @FXML private TableColumn<Pesanan, Pesanan.Status> colStatus;
    @FXML private TableColumn<Pesanan, Void> colAksi;

    private final PesananService pesananService;
    private final SessionManager sessionManager;
    private final SceneManager sceneManager;

    public AdminDashboardController(PesananService pesananService,
                                    SessionManager sessionManager,
                                    SceneManager sceneManager) {
        this.pesananService = pesananService;
        this.sessionManager = sessionManager;
        this.sceneManager = sceneManager;
    }

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        lblAdminName.setText(sessionManager.getCurrentUser().getNamaLengkap());
        setupTable();
        loadData();

        tfCari.textProperty().addListener((obs, o, n) -> handleCari());
    }

    private void setupTable() {
        colId.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().getKodePesanan()));
        colPengguna.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(
                abbreviateName(c.getValue().getUser().getNamaLengkap())));
        colLayanan.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(
                c.getValue().getLayanan().getNamaLayanan()));
        colTanggal.setCellValueFactory(new PropertyValueFactory<>("tanggalMasuk"));
        colTanggal.setCellFactory(col -> new TableCell<>() {
            private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("d MMM yyyy", new java.util.Locale("id", "ID"));
            @Override protected void updateItem(LocalDate item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.format(FMT));
            }
        });

        // Status badge cell
        colStatus.setCellValueFactory(new PropertyValueFactory<>("status"));
        colStatus.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(Pesanan.Status item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setGraphic(null); return; }
                Label badge = new Label(item.name());
                badge.getStyleClass().add("badge");
                badge.getStyleClass().add("badge-" + item.name().toLowerCase());
                setGraphic(badge);
                setText(null);
            }
        });

        // Edit button
        colAksi.setCellFactory(col -> new TableCell<>() {
            private final Button btn = new Button("Edit");
            {
                btn.getStyleClass().add("btn-edit");
                btn.setOnAction(e -> {
                    Pesanan p = getTableView().getItems().get(getIndex());
                    showEditDialog(p);
                });
            }
            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : btn);
            }
        });

        tablePesanan.setPlaceholder(new Label("Tidak ada pesanan."));
    }

    private void loadData() {
        List<Pesanan> list = pesananService.getPesananTerkini();
        tablePesanan.setItems(FXCollections.observableArrayList(list));

        lblDiproses.setText(String.valueOf(pesananService.countByStatus(Pesanan.Status.DIPROSES)));
        lblAktif.setText(String.valueOf(
                pesananService.countByStatus(Pesanan.Status.DIPROSES) +
                        pesananService.countByStatus(Pesanan.Status.SELESAI)));
        lblDiambil.setText(String.valueOf(pesananService.countByStatus(Pesanan.Status.DIAMBIL)));
    }

    @FXML
    private void handleCari() {
        String keyword = tfCari.getText().trim();
        if (keyword.isEmpty()) {
            loadData();
            return;
        }
        List<Pesanan> hasil = pesananService.cariPesanan(keyword);
        tablePesanan.setItems(FXCollections.observableArrayList(hasil));
    }

    private void showEditDialog(Pesanan pesanan) {
        Dialog<Pesanan.Status> dialog = new Dialog<>();
        dialog.setTitle("Edit Status Pesanan");
        dialog.setHeaderText("Pesanan: " + pesanan.getKodePesanan());

        ComboBox<Pesanan.Status> combo = new ComboBox<>(FXCollections.observableArrayList(Pesanan.Status.values()));
        combo.setValue(pesanan.getStatus());
        combo.getStyleClass().add("combo-status");

        dialog.getDialogPane().setContent(combo);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dialog.setResultConverter(btn -> btn == ButtonType.OK ? combo.getValue() : null);

        dialog.showAndWait().ifPresent(status -> {
            pesananService.updateStatus(pesanan.getIdPesanan(), status);
            loadData();
        });
    }

    @FXML private void handleGoLayanan() {
        try { sceneManager.switchTo("/com/washie/view/KelolaLayananView.fxml"); }
        catch (Exception e) { e.printStackTrace(); }
    }

    @FXML private void handleGoInfo() {
        try { sceneManager.switchTo("/com/washie/view/InfoLaundryView.fxml"); }
        catch (Exception e) { e.printStackTrace(); }
    }

    @FXML private void handleKeluar() {
        try {
            sessionManager.logout();
            sceneManager.switchTo("/com/washie/view/LoginView.fxml");
        } catch (Exception e) { e.printStackTrace(); }
    }

    private String abbreviateName(String full) {
        if (full == null) return "";
        String[] parts = full.split(" ");
        if (parts.length == 1) return full;
        return parts[0] + " " + parts[parts.length - 1].charAt(0) + ".";
    }
}

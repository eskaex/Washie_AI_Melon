package com.washie.controller;

import com.washie.model.Layanan;
import com.washie.service.LayananService;
import com.washie.util.SceneManager;
import com.washie.util.SessionManager;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import org.springframework.stereotype.Controller;

import java.net.URL;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;

@Controller
public class KelolaLayananController implements Initializable {

    @FXML private Label lblAdminName;
    @FXML private TextField tfCari;
    @FXML private ComboBox<String> cbFilter;
    @FXML private Label lblJumlah;

    @FXML private TableView<Layanan> tableLayanan;
    @FXML private TableColumn<Layanan, String> colNama;
    @FXML private TableColumn<Layanan, Double> colHarga;
    @FXML private TableColumn<Layanan, String> colEstimasi;
    @FXML private TableColumn<Layanan, Layanan.Status> colStatus;
    @FXML private TableColumn<Layanan, Void> colAksi;

    private final LayananService layananService;
    private final SessionManager sessionManager;
    private final SceneManager sceneManager;

    public KelolaLayananController(LayananService layananService,
                                   SessionManager sessionManager,
                                   SceneManager sceneManager) {
        this.layananService = layananService;
        this.sessionManager = sessionManager;
        this.sceneManager = sceneManager;
    }

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        lblAdminName.setText(sessionManager.getCurrentUser().getNamaLengkap());
        cbFilter.setItems(FXCollections.observableArrayList("Semua Status", "AKTIF", "NONAKTIF"));
        cbFilter.setValue("Semua Status");
        cbFilter.setOnAction(e -> loadData());
        tfCari.textProperty().addListener((obs, o, n) -> loadData());

        setupTable();
        loadData();
    }

    private void setupTable() {
        colNama.setCellValueFactory(new PropertyValueFactory<>("namaLayanan"));
        colHarga.setCellValueFactory(new PropertyValueFactory<>("harga"));
        colHarga.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : String.format("Rp%.0f/kg", item));
                if (!empty) getStyleClass().add("harga-cell");
            }
        });
        colEstimasi.setCellValueFactory(new PropertyValueFactory<>("estimasiWaktu"));

        colStatus.setCellValueFactory(new PropertyValueFactory<>("status"));
        colStatus.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(Layanan.Status item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setGraphic(null); return; }
                Label badge = new Label(item.name());
                badge.getStyleClass().add("badge");
                badge.getStyleClass().add(item == Layanan.Status.AKTIF ? "badge-aktif" : "badge-nonaktif");
                setGraphic(badge); setText(null);
            }
        });

        colAksi.setCellFactory(col -> new TableCell<>() {
            private final Button btnEdit   = new Button("Edit");
            private final Button btnHapus  = new Button("Hapus");
            private final javafx.scene.layout.HBox box = new javafx.scene.layout.HBox(6, btnEdit, btnHapus);
            {
                btnEdit.getStyleClass().add("btn-edit");
                btnHapus.getStyleClass().add("btn-hapus");
                btnEdit.setOnAction(e -> showEditDialog(getTableView().getItems().get(getIndex())));
                btnHapus.setOnAction(e -> handleHapus(getTableView().getItems().get(getIndex())));
            }
            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : box);
            }
        });
    }

    private void loadData() {
        String keyword = tfCari.getText().trim();
        String filter  = cbFilter.getValue();

        List<Layanan> list = keyword.isEmpty() ? layananService.getAllLayanan()
                : layananService.cariLayanan(keyword);

        if ("AKTIF".equals(filter)) {
            list = list.stream().filter(l -> l.getStatus() == Layanan.Status.AKTIF).toList();
        } else if ("NONAKTIF".equals(filter)) {
            list = list.stream().filter(l -> l.getStatus() == Layanan.Status.NONAKTIF).toList();
        }

        tableLayanan.setItems(FXCollections.observableArrayList(list));
        lblJumlah.setText(list.size() + " Layanan");
    }

    @FXML
    private void handleTambah() {
        showFormDialog(null);
    }

    private void showEditDialog(Layanan layanan) {
        showFormDialog(layanan);
    }

    private void showFormDialog(Layanan existing) {
        Dialog<Layanan> dialog = new Dialog<>();
        dialog.setTitle(existing == null ? "Tambah Layanan" : "Edit Layanan");

        javafx.scene.layout.GridPane grid = new javafx.scene.layout.GridPane();
        grid.setHgap(12); grid.setVgap(10);
        grid.setPadding(new javafx.geometry.Insets(16));

        TextField tfNama = new TextField(existing != null ? existing.getNamaLayanan() : "");
        tfNama.setPromptText("Nama layanan");
        TextField tfHarga = new TextField(existing != null ? String.format("%.0f", existing.getHarga()) : "");
        tfHarga.setPromptText("Harga per kg");
        TextField tfEstimasi = new TextField(existing != null ? existing.getEstimasiWaktu() : "");
        tfEstimasi.setPromptText("cth: 2 Hari Kerja");
        ComboBox<Layanan.Status> cbStatus = new ComboBox<>(FXCollections.observableArrayList(Layanan.Status.values()));
        cbStatus.setValue(existing != null ? existing.getStatus() : Layanan.Status.AKTIF);

        grid.add(new Label("Nama Layanan:"), 0, 0); grid.add(tfNama, 1, 0);
        grid.add(new Label("Harga (Rp/kg):"), 0, 1); grid.add(tfHarga, 1, 1);
        grid.add(new Label("Estimasi Waktu:"), 0, 2); grid.add(tfEstimasi, 1, 2);
        grid.add(new Label("Status:"), 0, 3); grid.add(cbStatus, 1, 3);

        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dialog.setResultConverter(btn -> {
            if (btn != ButtonType.OK) return null;
            try {
                Layanan l = existing != null ? existing : new Layanan();
                l.setNamaLayanan(tfNama.getText().trim());
                l.setHarga(Double.parseDouble(tfHarga.getText().trim()));
                l.setEstimasiWaktu(tfEstimasi.getText().trim());
                l.setStatus(cbStatus.getValue());
                return l;
            } catch (NumberFormatException ex) {
                return null;
            }
        });

        dialog.showAndWait().ifPresent(l -> {
            layananService.save(l);
            loadData();
        });
    }

    private void handleHapus(Layanan layanan) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Hapus layanan \"" + layanan.getNamaLayanan() + "\"?",
                ButtonType.YES, ButtonType.NO);
        confirm.setTitle("Konfirmasi Hapus");
        confirm.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.YES) {
                layananService.hapusLayanan(layanan.getIdLayanan());
                loadData();
            }
        });
    }

    @FXML private void handleGoDashboard() {
        try { sceneManager.switchTo("/com/washie/view/AdminDashboardView.fxml"); }
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
}

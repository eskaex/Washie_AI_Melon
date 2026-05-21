package com.washie.controller;

import com.washie.model.Layanan;
import com.washie.service.LayananService;
import com.washie.util.SceneManager;
import com.washie.util.SessionManager;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import org.springframework.stereotype.Controller;

import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;

@Controller
public class KelolaLayananController implements Initializable {

    @FXML private Label     lblAdminName;
    @FXML private TabPane   tabPane;

    // ── Tab Layanan Utama ──────────────────────────────────────────────────
    @FXML private TextField tfCariLayanan;
    @FXML private ComboBox<String> cbFilterLayanan;
    @FXML private Label     lblJumlahLayanan;
    @FXML private TableView<Layanan>                  tableLayanan;
    @FXML private TableColumn<Layanan, String>        colNama;
    @FXML private TableColumn<Layanan, Double>        colHarga;
    @FXML private TableColumn<Layanan, String>        colEstimasi;
    @FXML private TableColumn<Layanan, Boolean>       colExpress;
    @FXML private TableColumn<Layanan, Layanan.Status> colStatus;
    @FXML private TableColumn<Layanan, Void>          colAksi;

    // ── Tab Add-on ─────────────────────────────────────────────────────────
    @FXML private VBox      vboxAddonList;

    private final LayananService layananService;
    private final SessionManager sessionManager;
    private final SceneManager   sceneManager;

    public KelolaLayananController(LayananService l, SessionManager s, SceneManager sc) {
        this.layananService = l; this.sessionManager = s; this.sceneManager = sc;
    }

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        lblAdminName.setText(sessionManager.getCurrentUser().getNamaLengkap());

        cbFilterLayanan.setItems(FXCollections.observableArrayList("Semua Status","AKTIF","NONAKTIF"));
        cbFilterLayanan.setValue("Semua Status");
        cbFilterLayanan.setOnAction(e -> loadLayanan());
        tfCariLayanan.textProperty().addListener((obs,o,n) -> loadLayanan());

        setupTableLayanan();
        loadLayanan();
        loadAddon();
    }

    // =========================================================================
    //  TAB 1 — LAYANAN UTAMA
    // =========================================================================
    private void setupTableLayanan() {
        colNama.setCellValueFactory(new PropertyValueFactory<>("namaLayanan"));

        colHarga.setCellValueFactory(new PropertyValueFactory<>("harga"));
        colHarga.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); return; }
                Layanan l = getTableView().getItems().get(getIndex());
                setText("Rp" + String.format("%.0f", item) + (l.isPerKg() ? "/kg" : "/item"));
                getStyleClass().add("harga-cell");
            }
        });

        colEstimasi.setCellValueFactory(new PropertyValueFactory<>("estimasiWaktu"));

        colExpress.setCellValueFactory(new PropertyValueFactory<>("bisaExpress"));
        colExpress.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(Boolean v, boolean empty) {
                super.updateItem(v, empty);
                if (empty || v == null) { setGraphic(null); return; }
                Label badge = new Label(v ? "Ya" : "Tidak");
                badge.getStyleClass().add("badge");
                badge.getStyleClass().add(v ? "badge-aktif" : "badge-nonaktif");
                setGraphic(badge); setText(null);
            }
        });

        colStatus.setCellValueFactory(new PropertyValueFactory<>("status"));
        colStatus.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(Layanan.Status v, boolean empty) {
                super.updateItem(v, empty);
                if (empty || v == null) { setGraphic(null); return; }
                Label badge = new Label(v.name());
                badge.getStyleClass().add("badge");
                badge.getStyleClass().add(v == Layanan.Status.AKTIF ? "badge-aktif" : "badge-nonaktif");
                setGraphic(badge); setText(null);
            }
        });

        colAksi.setCellFactory(col -> new TableCell<>() {
            private final Button btnEdit  = new Button("Edit");
            private final Button btnHapus = new Button("Hapus");
            private final HBox box = new HBox(6, btnEdit, btnHapus);
            {
                btnEdit.getStyleClass().add("btn-edit");
                btnHapus.getStyleClass().add("btn-hapus");
                btnEdit.setOnAction(e  -> showFormLayanan(getTableView().getItems().get(getIndex())));
                btnHapus.setOnAction(e -> hapusLayanan(getTableView().getItems().get(getIndex())));
            }
            @Override protected void updateItem(Void v, boolean empty) {
                super.updateItem(v, empty);
                setGraphic(empty ? null : box);
            }
        });

        tableLayanan.setPlaceholder(new Label("Tidak ada layanan."));
    }

    private void loadLayanan() {
        String kw    = tfCariLayanan.getText().trim();
        String filter = cbFilterLayanan.getValue();

        List<Layanan> list = kw.isEmpty()
                ? layananService.getAll().stream().filter(l -> l.getTipe() == Layanan.Tipe.LAYANAN).toList()
                : layananService.cari(kw).stream().filter(l -> l.getTipe() == Layanan.Tipe.LAYANAN).toList();

        if ("AKTIF".equals(filter))    list = list.stream().filter(l -> l.getStatus() == Layanan.Status.AKTIF).toList();
        if ("NONAKTIF".equals(filter)) list = list.stream().filter(l -> l.getStatus() == Layanan.Status.NONAKTIF).toList();

        tableLayanan.setItems(FXCollections.observableArrayList(list));
        lblJumlahLayanan.setText(list.size() + " Layanan");
    }

    @FXML private void handleTambahLayanan() { showFormLayanan(null); }

    private void showFormLayanan(Layanan existing) {
        Dialog<Layanan> dialog = new Dialog<>();
        dialog.setTitle(existing == null ? "Tambah Layanan" : "Edit Layanan");

        GridPane grid = new GridPane();
        grid.setHgap(12); grid.setVgap(10);
        grid.setPadding(new Insets(16));

        TextField  tfNama     = new TextField(existing != null ? existing.getNamaLayanan() : "");
        TextField  tfHarga    = new TextField(existing != null ? String.format("%.0f", existing.getHarga()) : "");
        TextField  tfEstimasi = new TextField(existing != null ? nvl(existing.getEstimasiWaktu()) : "");
        CheckBox   cbExpress  = new CheckBox("Tersedia Express");
        CheckBox   cbPerKg    = new CheckBox("Harga per Kg (uncheck = per Item)");
        ComboBox<Layanan.Status> cbStatus = new ComboBox<>(
                FXCollections.observableArrayList(Layanan.Status.values()));

        cbExpress.setSelected(existing != null && existing.isBisaExpress());
        cbPerKg.setSelected(existing == null || existing.isPerKg());
        cbStatus.setValue(existing != null ? existing.getStatus() : Layanan.Status.AKTIF);

        tfNama.setPromptText("Nama layanan");
        tfHarga.setPromptText("Harga (angka saja)");
        tfEstimasi.setPromptText("cth: 2 Hari Kerja");

        grid.add(new Label("Nama Layanan:"), 0, 0); grid.add(tfNama, 1, 0);
        grid.add(new Label("Harga (Rp):"),   0, 1); grid.add(tfHarga, 1, 1);
        grid.add(new Label("Estimasi:"),      0, 2); grid.add(tfEstimasi, 1, 2);
        grid.add(cbPerKg,                     0, 3, 2, 1);
        grid.add(cbExpress,                   0, 4, 2, 1);
        grid.add(new Label("Status:"),        0, 5); grid.add(cbStatus, 1, 5);

        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dialog.setResultConverter(btn -> {
            if (btn != ButtonType.OK) return null;
            try {
                Layanan l = existing != null ? existing : new Layanan();
                l.setNamaLayanan(tfNama.getText().trim());
                l.setHarga(Double.parseDouble(tfHarga.getText().trim()));
                l.setEstimasiWaktu(tfEstimasi.getText().trim());
                l.setPerKg(cbPerKg.isSelected());
                l.setBisaExpress(cbExpress.isSelected());
                l.setStatus(cbStatus.getValue());
                l.setTipe(Layanan.Tipe.LAYANAN);
                return l;
            } catch (NumberFormatException ex) { return null; }
        });

        dialog.showAndWait().ifPresent(l -> { layananService.save(l); loadLayanan(); });
    }

    private void hapusLayanan(Layanan l) {
        Alert c = new Alert(Alert.AlertType.CONFIRMATION,
                "Hapus layanan \"" + l.getNamaLayanan() + "\"?", ButtonType.YES, ButtonType.NO);
        c.setTitle("Konfirmasi");
        c.showAndWait().filter(b -> b == ButtonType.YES)
                .ifPresent(b -> { layananService.hapus(l.getIdLayanan()); loadLayanan(); });
    }

    // =========================================================================
    //  TAB 2 — ADD-ON
    // =========================================================================
    private void loadAddon() {
        if (vboxAddonList == null) return;
        vboxAddonList.getChildren().clear();

        List<Layanan> addons = layananService.getAll().stream()
                .filter(l -> l.getTipe() == Layanan.Tipe.ADDON).toList();

        if (addons.isEmpty()) {
            vboxAddonList.getChildren().add(new Label("Belum ada add-on."));
            return;
        }

        for (Layanan a : addons) {
            HBox row = new HBox(12);
            row.setAlignment(Pos.CENTER_LEFT);
            row.setPadding(new Insets(8, 12, 8, 12));
            row.setStyle("-fx-background-color:#FFFFFF; -fx-background-radius:8; " +
                    "-fx-border-color:#D0D8E0; -fx-border-radius:8; " +
                    "-fx-border-width:1;");

            // Status badge
            Label badgeStatus = new Label(a.getStatus().name());
            badgeStatus.getStyleClass().add("badge");
            badgeStatus.getStyleClass().add(a.getStatus() == Layanan.Status.AKTIF ? "badge-aktif" : "badge-nonaktif");

            Label lblNama  = new Label(a.getNamaLayanan());
            lblNama.setPrefWidth(220);
            lblNama.setStyle("-fx-font-size:13px; -fx-font-weight:bold; -fx-text-fill:#1A3A5C;");
            HBox.setHgrow(lblNama, Priority.ALWAYS);

            Label lblHarga = new Label("Rp" + String.format("%.0f", a.getHarga()));
            lblHarga.setStyle("-fx-font-size:13px; -fx-text-fill:#1E6FA8; -fx-font-weight:bold;");
            lblHarga.setPrefWidth(100);

            Button btnEdit  = new Button("Edit");
            Button btnHapus = new Button("Hapus");
            btnEdit.getStyleClass().add("btn-edit");
            btnHapus.getStyleClass().add("btn-hapus");

            btnEdit.setOnAction(ev  -> { showFormAddon(a); loadAddon(); });
            btnHapus.setOnAction(ev -> { hapusAddon(a); loadAddon(); });

            row.getChildren().addAll(lblNama, lblHarga, badgeStatus, btnEdit, btnHapus);
            vboxAddonList.getChildren().add(row);
        }
    }

    @FXML private void handleTambahAddon() {
        showFormAddon(null);
        loadAddon();
    }

    private void showFormAddon(Layanan existing) {
        Dialog<Layanan> dialog = new Dialog<>();
        dialog.setTitle(existing == null ? "Tambah Add-on" : "Edit Add-on");

        GridPane grid = new GridPane();
        grid.setHgap(12); grid.setVgap(10);
        grid.setPadding(new Insets(16));

        TextField tfNama  = new TextField(existing != null ? existing.getNamaLayanan() : "");
        TextField tfHarga = new TextField(existing != null ? String.format("%.0f", existing.getHarga()) : "");
        ComboBox<Layanan.Status> cbStatus = new ComboBox<>(
                FXCollections.observableArrayList(Layanan.Status.values()));
        cbStatus.setValue(existing != null ? existing.getStatus() : Layanan.Status.AKTIF);

        tfNama.setPromptText("cth: Pewangi Lavender");
        tfHarga.setPromptText("cth: 3000");

        Label lblInfo = new Label("Add-on aktif akan muncul sebagai pilihan di chatbot.");
        lblInfo.setStyle("-fx-text-fill:#888; -fx-font-size:11px;");

        grid.add(new Label("Nama Add-on:"), 0, 0); grid.add(tfNama, 1, 0);
        grid.add(new Label("Harga (Rp):"),  0, 1); grid.add(tfHarga, 1, 1);
        grid.add(new Label("Status:"),      0, 2); grid.add(cbStatus, 1, 2);
        grid.add(lblInfo,                   0, 3, 2, 1);

        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dialog.setResultConverter(btn -> {
            if (btn != ButtonType.OK) return null;
            try {
                Layanan a = existing != null ? existing : new Layanan();
                a.setNamaLayanan(tfNama.getText().trim());
                a.setHarga(Double.parseDouble(tfHarga.getText().trim()));
                a.setStatus(cbStatus.getValue());
                a.setTipe(Layanan.Tipe.ADDON);
                a.setPerKg(false);
                a.setBisaExpress(false);
                return a;
            } catch (NumberFormatException ex) { return null; }
        });

        dialog.showAndWait().ifPresent(a -> { layananService.save(a); loadAddon(); });
    }

    private void hapusAddon(Layanan a) {
        Alert c = new Alert(Alert.AlertType.CONFIRMATION,
                "Hapus add-on \"" + a.getNamaLayanan() + "\"?", ButtonType.YES, ButtonType.NO);
        c.setTitle("Konfirmasi");
        c.showAndWait().filter(b -> b == ButtonType.YES)
                .ifPresent(b -> layananService.hapus(a.getIdLayanan()));
    }

    // =========================================================================
    //  Navigation
    // =========================================================================
    @FXML private void handleGoDashboard() {
        try { sceneManager.switchTo("/com/washie/view/AdminDashboardView.fxml"); } catch (Exception e) { e.printStackTrace(); }
    }
    @FXML private void handleGoInfo() {
        try { sceneManager.switchTo("/com/washie/view/InfoLaundryView.fxml"); } catch (Exception e) { e.printStackTrace(); }
    }
    @FXML private void handleKeluar() {
        try { sessionManager.logout(); sceneManager.switchTo("/com/washie/view/LoginView.fxml"); } catch (Exception e) { e.printStackTrace(); }
    }

    private String nvl(String s) { return s == null ? "" : s; }
}

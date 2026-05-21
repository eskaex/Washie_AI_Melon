package com.washie.controller;

import com.washie.model.Pesanan;
import com.washie.model.PesananItem;
import com.washie.service.PesananService;
import com.washie.util.SceneManager;
import com.washie.util.SessionManager;
import javafx.beans.property.SimpleStringProperty;
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
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;

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
    @FXML private TableColumn<Pesanan, String> colTotal;
    @FXML private TableColumn<Pesanan, Pesanan.Status> colStatus;
    @FXML private TableColumn<Pesanan, Void> colAksi;

    private final PesananService pesananService;
    private final SessionManager sessionManager;
    private final SceneManager   sceneManager;

    public AdminDashboardController(PesananService p, SessionManager s, SceneManager sc) {
        this.pesananService = p; this.sessionManager = s; this.sceneManager = sc;
    }

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        lblAdminName.setText(sessionManager.getCurrentUser().getNamaLengkap());
        setupTable();
        loadData();
        tfCari.textProperty().addListener((obs, o, n) -> handleCari());
    }

    private void setupTable() {
        colId.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getKodePesanan()));

        colPengguna.setCellValueFactory(c ->
                new SimpleStringProperty(singkatNama(c.getValue().getUser().getNamaLengkap())));

        colLayanan.setCellValueFactory(c -> {
            Pesanan p   = c.getValue();
            String nama = p.getLayanan() != null ? p.getLayanan().getNamaLayanan() : "-";
            int total   = p.getItems() != null ? p.getItems().size() : 0;
            String extra = total > 1 ? " +" + (total - 1) + " lagi" : "";
            return new SimpleStringProperty(nama + extra);
        });

        colTanggal.setCellValueFactory(new PropertyValueFactory<>("tanggalMasuk"));
        colTanggal.setCellFactory(col -> new TableCell<>() {
            private static final DateTimeFormatter FMT =
                    DateTimeFormatter.ofPattern("d MMM yyyy", new Locale("id","ID"));
            @Override protected void updateItem(LocalDate item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.format(FMT));
            }
        });

        colTotal.setCellValueFactory(c -> {
            Double total = c.getValue().getTotalHarga();
            return new SimpleStringProperty(total != null ? "Rp" + fmt(total) : "-");
        });
        colTotal.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item);
                if (!empty) setStyle("-fx-text-fill:#1E6FA8; -fx-font-weight:bold;");
            }
        });

        colStatus.setCellValueFactory(new PropertyValueFactory<>("status"));
        colStatus.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(Pesanan.Status item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setGraphic(null); return; }
                Label badge = new Label(item.name());
                badge.getStyleClass().add("badge");
                badge.getStyleClass().add("badge-" + item.name().toLowerCase());
                setGraphic(badge); setText(null);
            }
        });

        colAksi.setCellFactory(col -> new TableCell<>() {
            private final Button btnDetail = new Button("Detail");
            private final Button btnEdit = new Button("Edit");
            private final HBox box = new HBox(6, btnDetail, btnEdit);
            {
                btnDetail.getStyleClass().add("btn-edit");
                btnEdit.getStyleClass().add("btn-edit");
                btnDetail.setOnAction(e -> showDetailDialog(getTableView().getItems().get(getIndex())));
                btnEdit.setOnAction(e   -> showEditDialog(getTableView().getItems().get(getIndex())));
            }
            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : box);
            }
        });

        tablePesanan.setPlaceholder(new Label("Belum ada pesanan."));
    }

    private void loadData() {
        tablePesanan.setItems(FXCollections.observableArrayList(pesananService.getPesananTerkini()));
        lblDiproses.setText(String.valueOf(pesananService.countByStatus(Pesanan.Status.DIPROSES)));
        lblAktif.setText(String.valueOf(
                pesananService.countByStatus(Pesanan.Status.DIPROSES) +
                        pesananService.countByStatus(Pesanan.Status.SELESAI)));
        lblDiambil.setText(String.valueOf(pesananService.countByStatus(Pesanan.Status.DIAMBIL)));
    }

    @FXML private void handleCari() {
        String kw = tfCari.getText().trim();
        if (kw.isEmpty()) { loadData(); return; }
        tablePesanan.setItems(FXCollections.observableArrayList(pesananService.cariPesanan(kw)));
    }

    @FXML private void handleRefresh() { tfCari.clear(); loadData(); }

    private void showDetailDialog(Pesanan p) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Detail Pesanan — " + p.getKodePesanan());
        dialog.getDialogPane().setPrefWidth(500);

        ScrollPane scroll = new ScrollPane();
        scroll.setFitToWidth(true);
        scroll.setPrefHeight(500);

        VBox content = new VBox(10);
        content.setPadding(new Insets(16));

        content.getChildren().add(baris("ID Pesanan", p.getKodePesanan(), false));
        content.getChildren().add(baris("Pengguna", p.getUser().getNamaLengkap(), false));
        content.getChildren().add(baris("Tanggal Masuk",
                p.getTanggalMasuk() != null ? p.getTanggalMasuk().toString() : "-", false));
        content.getChildren().add(baris("Status", p.getStatus().name(), false));
        content.getChildren().add(new Separator());

        List<PesananItem> items = p.getItems();
        if (items == null || items.isEmpty()) {
            Label lbl = new Label("(Tidak ada detail item tersimpan)");
            lbl.setStyle("-fx-text-fill:#aaa;");
            content.getChildren().add(lbl);
        } else {
            int no = 1;
            for (PesananItem item : items) {
                Label lblItem = new Label("ITEM " + no++ + " — " + item.getLayanan().getNamaLayanan());
                lblItem.setStyle("-fx-font-weight:bold; -fx-text-fill:#1A3A5C; -fx-font-size:13px;");
                content.getChildren().add(lblItem);

                if (item.getBeratKg() != null && item.getBeratKg() > 0)
                    content.getChildren().add(baris("  Berat", item.getBeratKg() + " kg", false));
                else if (item.getJumlahItem() != null && item.getJumlahItem() > 0)
                    content.getChildren().add(baris("  Jumlah", item.getJumlahItem() + " item", false));

                content.getChildren().add(baris("  Kecepatan",
                        item.getKecepatan() != null ? item.getKecepatan() : "-", false));

                if (item.getAddonNama() != null && !item.getAddonNama().isBlank()) {
                    Label lblA = new Label("  Add-on:");
                    lblA.setStyle("-fx-font-weight:bold; -fx-text-fill:#555;");
                    content.getChildren().add(lblA);
                    for (String a : item.getAddonNama().split(",")) {
                        Label la = new Label("    - " + a.trim());
                        la.setStyle("-fx-text-fill:#444; -fx-font-size:12px;");
                        content.getChildren().add(la);
                    }
                }

                content.getChildren().add(new Separator());

                if (item.getSubtotalLayanan() != null)
                    content.getChildren().add(baris("  Subtotal Layanan",
                            "Rp" + fmt(item.getSubtotalLayanan()), false));
                if (item.getExpressTotal() != null && item.getExpressTotal() > 0)
                    content.getChildren().add(baris("  Express",
                            "+Rp" + fmt(item.getExpressTotal()), false));
                if (item.getAddonTotal() != null && item.getAddonTotal() > 0)
                    content.getChildren().add(baris("  Total Add-on",
                            "+Rp" + fmt(item.getAddonTotal()), false));
                if (item.getTotalItem() != null)
                    content.getChildren().add(baris("  TOTAL ITEM",
                            "Rp" + fmt(item.getTotalItem()), true));

                content.getChildren().add(new Separator());
            }
        }

        if (p.getTotalHarga() != null) {
            Label lblGrand = new Label("TOTAL KESELURUHAN: Rp" + fmt(p.getTotalHarga()));
            lblGrand.setStyle("-fx-font-size:15px; -fx-font-weight:bold; -fx-text-fill:#1E6FA8;");
            content.getChildren().add(lblGrand);
        }

        scroll.setContent(content);
        dialog.getDialogPane().setContent(scroll);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.showAndWait();
    }

    private void showEditDialog(Pesanan p) {
        Dialog<Pesanan.Status> dialog = new Dialog<>();
        dialog.setTitle("Edit Status — " + p.getKodePesanan());
        dialog.setHeaderText(p.getUser().getNamaLengkap());

        ComboBox<Pesanan.Status> combo = new ComboBox<>(
                FXCollections.observableArrayList(Pesanan.Status.values()));
        combo.setValue(p.getStatus());
        dialog.getDialogPane().setContent(combo);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dialog.setResultConverter(btn -> btn == ButtonType.OK ? combo.getValue() : null);

        dialog.showAndWait().ifPresent(status -> {
            pesananService.updateStatus(p.getIdPesanan(), status);
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
        try { sessionManager.logout(); sceneManager.switchTo("/com/washie/view/LoginView.fxml"); }
        catch (Exception e) { e.printStackTrace(); }
    }

    private HBox baris(String label, String value, boolean bold) {
        Label lbl = new Label(label + ":");
        lbl.setPrefWidth(160);
        lbl.setStyle("-fx-font-weight:bold; -fx-text-fill:#555;");
        Label val = new Label(value != null ? value : "-");
        val.setStyle("-fx-text-fill:" + (bold ? "#1E6FA8" : "#1A1A2E") + ";" +
                (bold ? "-fx-font-weight:bold; -fx-font-size:14px;" : ""));
        val.setWrapText(true);
        HBox box = new HBox(8, lbl, val);
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }

    private String singkatNama(String full) {
        if (full == null || full.isBlank()) return "";
        String[] parts = full.trim().split("\\s+");
        return parts.length == 1 ? full : parts[0] + " " + parts[parts.length-1].charAt(0) + ".";
    }

    private String fmt(double v) {
        return v == Math.floor(v) ? String.format("%.0f", v) : String.format("%.1f", v);
    }
}

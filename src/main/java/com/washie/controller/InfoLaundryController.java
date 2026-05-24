package com.washie.controller;

import com.washie.model.InfoEntity;
import com.washie.service.InfoService;
import com.washie.util.SceneManager;
import com.washie.util.SessionManager;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import org.springframework.stereotype.Controller;

import java.net.URL;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.ResourceBundle;

@Controller
public class InfoLaundryController implements Initializable {

    @FXML private Label lblNamaUsaha;
    @FXML private Label lblPemilik;
    @FXML private Label lblTahunBerdiri;
    @FXML private Label lblUpdatedIdentitas;
    @FXML private Label lblAlamat;
    @FXML private Label lblWa;
    @FXML private Label lblIg;
    @FXML private Label lblUpdatedLokasi;
    @FXML private Label lblSenJum;
    @FXML private Label lblSabMinggu;
    @FXML private Label lblLibur;
    @FXML private Label lblUpdatedJam;
    @FXML private FlowPane flowPengumuman;
    @FXML private Label lblAdminName;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("d MMM yyyy");

    private final InfoService    infoService;
    private final SessionManager sessionManager;
    private final SceneManager   sceneManager;

    public InfoLaundryController(InfoService i, SessionManager s, SceneManager sc) {
        this.infoService = i; this.sessionManager = s; this.sceneManager = sc;
    }

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        lblAdminName.setText(sessionManager.getCurrentUser().getNamaLengkap());
        loadData();
    }

    private void loadData() {
        set(lblNamaUsaha, "IDENTITAS",      "nama_usaha");
        set(lblPemilik,"IDENTITAS",      "pemilik");
        set(lblTahunBerdiri, "IDENTITAS",      "tahun_berdiri");
        setUpdated(lblUpdatedIdentitas, "IDENTITAS");

        set(lblAlamat, "LOKASI_KONTAK", "alamat");
        set(lblWa, "LOKASI_KONTAK", "whatsapp");
        set(lblIg, "LOKASI_KONTAK", "instagram");
        setUpdated(lblUpdatedLokasi, "LOKASI_KONTAK");

        set(lblSenJum, "JAM_OPERASIONAL", "senin_jumat");
        set(lblSabMinggu, "JAM_OPERASIONAL", "sabtu_minggu");
        set(lblLibur, "JAM_OPERASIONAL", "hari_libur");
        setUpdated(lblUpdatedJam, "JAM_OPERASIONAL");

        loadPengumuman();
    }

    private void loadPengumuman() {
        if (flowPengumuman == null) return;
        flowPengumuman.getChildren().clear();

        List<InfoEntity> list = infoService.getPengumuman();
        if (list.isEmpty()) {
            Label empty = new Label("Belum ada informasi tambahan.\nKlik '+ Tambah Informasi' untuk membuat pengumuman.");
            empty.setStyle("-fx-text-fill:#aaa; -fx-font-size:12px;");
            flowPengumuman.getChildren().add(empty);
            return;
        }

        for (InfoEntity e : list) {
            flowPengumuman.getChildren().add(buildPengumumanCard(e));
        }
    }

    private VBox buildPengumumanCard(InfoEntity e) {
        VBox card = new VBox(8);
        card.setPrefWidth(300);
        card.setMaxWidth(340);
        card.setPadding(new Insets(14));
        card.setStyle("-fx-background-color:white; -fx-background-radius:10; " + "-fx-border-color:#E0E8F0; -fx-border-radius:10; " + "-fx-effect: dropshadow(gaussian,rgba(0,0,0,0.07),6,0,0,2);");

        Label lblJudul = new Label(e.getKunci());
        lblJudul.setStyle("-fx-font-size:14px; -fx-font-weight:bold; -fx-text-fill:#1A3A5C;");
        lblJudul.setWrapText(true);

        Label lblIsi = new Label(e.getNilai());
        lblIsi.setStyle("-fx-font-size:12px; -fx-text-fill:#444;");
        lblIsi.setWrapText(true);
        lblIsi.setMaxWidth(300);
        VBox.setVgrow(lblIsi, Priority.ALWAYS);

        String tgl = e.getUpdatedAt() != null ? "Diperbarui: " + e.getUpdatedAt().format(FMT) : "";
        Label lblTgl = new Label(tgl);
        lblTgl.setStyle("-fx-font-size:10px; -fx-text-fill:#aaa; -fx-font-style:italic;");

        Button btnEdit  = new Button("Edit");
        Button btnHapus = new Button("Hapus");
        btnEdit.getStyleClass().add("btn-edit");
        btnHapus.getStyleClass().add("btn-hapus");
        btnEdit.setOnAction(ev  -> { showEditPengumuman(e); loadPengumuman(); });
        btnHapus.setOnAction(ev -> { hapusPengumuman(e); loadPengumuman(); });

        HBox actions = new HBox(8, btnEdit, btnHapus);
        actions.setAlignment(Pos.CENTER_RIGHT);

        card.getChildren().addAll(lblJudul, lblIsi, lblTgl, actions);
        return card;
    }

    @FXML
    private void handleTambahInformasi() {
        Dialog<Boolean> dialog = new Dialog<>();
        dialog.setTitle("Tambah Informasi / Pengumuman");
        dialog.setHeaderText("Buat kotak informasi baru — tampil sebagai card tersendiri.");

        GridPane grid = new GridPane();
        grid.setHgap(12); grid.setVgap(10);
        grid.setPadding(new Insets(16));

        TextField  tfJudul = new TextField();
        TextArea   taIsi   = new TextArea();
        tfJudul.setPromptText("cth: Promo Lebaran, Libur Hari Raya, Info Antrean...");
        taIsi.setPromptText("Isi pengumuman / informasi lengkap...");
        taIsi.setPrefHeight(100);
        taIsi.setWrapText(true);

        grid.add(new Label("Judul:"), 0, 0); grid.add(tfJudul, 1, 0);
        grid.add(new Label("Isi:"), 0, 1); grid.add(taIsi, 1, 1);

        GridPane.setHgrow(tfJudul, Priority.ALWAYS);
        GridPane.setHgrow(taIsi, Priority.ALWAYS);

        dialog.getDialogPane().setPrefWidth(480);
        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dialog.setResultConverter(btn -> {
            if (btn != ButtonType.OK) return null;
            String judul = tfJudul.getText().trim();
            String isi   = taIsi.getText().trim();
            if (judul.isEmpty() || isi.isEmpty()) return null;
            // Simpan: kategori=INFORMASI, kunci=judul, nilai=isi
            infoService.save(new InfoEntity("INFORMASI", judul, isi));
            return true;
        });

        dialog.showAndWait().ifPresent(ok -> { if (ok) loadPengumuman(); });
    }

    private void showEditPengumuman(InfoEntity e) {
        Dialog<Boolean> dialog = new Dialog<>();
        dialog.setTitle("Edit Informasi");

        GridPane grid = new GridPane();
        grid.setHgap(12); grid.setVgap(10);
        grid.setPadding(new Insets(16));

        TextField tfJudul = new TextField(e.getKunci());
        TextArea taIsi   = new TextArea(e.getNilai());
        taIsi.setPrefHeight(100); taIsi.setWrapText(true);

        grid.add(new Label("Judul:"), 0, 0); grid.add(tfJudul, 1, 0);
        grid.add(new Label("Isi:"), 0, 1); grid.add(taIsi, 1, 1);
        GridPane.setHgrow(tfJudul, Priority.ALWAYS);
        GridPane.setHgrow(taIsi, Priority.ALWAYS);

        dialog.getDialogPane().setPrefWidth(480);
        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dialog.setResultConverter(btn -> {
            if (btn != ButtonType.OK) return null;
            e.setKunci(tfJudul.getText().trim());
            e.setNilai(taIsi.getText().trim());
            infoService.save(e);
            return true;
        });

        dialog.showAndWait();
    }

    private void hapusPengumuman(InfoEntity e) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, "Hapus informasi \"" + e.getKunci() + "\"?", ButtonType.YES, ButtonType.NO);
        confirm.setTitle("Konfirmasi Hapus");
        confirm.showAndWait().filter(b -> b == ButtonType.YES)
                .ifPresent(b -> infoService.hapus(e.getId()));
    }

    @FXML private void handleEditIdentitas() { showEditFixed("IDENTITAS"); }
    @FXML private void handleEditLokasi() { showEditFixed("LOKASI_KONTAK"); }
    @FXML private void handleEditJam() { showEditFixed("JAM_OPERASIONAL"); }

    private void showEditFixed(String kategori) {
        List<InfoEntity> items = infoService.getByKategori(kategori);

        Dialog<Boolean> dialog = new Dialog<>();
        dialog.setTitle("Edit " + kategoriLabel(kategori));

        GridPane grid = new GridPane();
        grid.setHgap(12); grid.setVgap(10);
        grid.setPadding(new Insets(16));

        TextField[] fields = new TextField[items.size()];
        for (int i = 0; i < items.size(); i++) {
            InfoEntity e = items.get(i);
            Label lbl = new Label(kunciLabel(e.getKunci()) + ":");
            lbl.setStyle("-fx-font-weight:bold;");
            fields[i] = new TextField(e.getNilai());
            fields[i].setPrefWidth(280);
            grid.add(lbl, 0, i); grid.add(fields[i], 1, i);
        }

        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dialog.setResultConverter(btn -> {
            if (btn != ButtonType.OK) return null;
            for (int i = 0; i < items.size(); i++)
                infoService.saveOrUpdate(items.get(i).getKategori(), items.get(i).getKunci(), fields[i].getText().trim());
            return true;
        });

        dialog.showAndWait().ifPresent(ok -> { if (ok) loadData(); });
    }

    @FXML private void handleGoDashboard() {
        try { sceneManager.switchTo("/com/washie/view/AdminDashboardView.fxml"); } catch (Exception e) { e.printStackTrace(); }
    }
    @FXML private void handleGoLayanan() {
        try { sceneManager.switchTo("/com/washie/view/KelolaLayananView.fxml"); } catch (Exception e) { e.printStackTrace(); }
    }
    @FXML private void handleKeluar() {
        try { sessionManager.logout(); sceneManager.switchTo("/com/washie/view/LoginView.fxml"); } catch (Exception e) { e.printStackTrace(); }
    }

    private void set(Label label, String kategori, String kunci) {
        if (label == null) return;
        infoService.getNilai(kategori, kunci).ifPresent(label::setText);
    }

    private void setUpdated(Label label, String kategori) {
        if (label == null) return;
        infoService.getByKategori(kategori).stream()
                .filter(e -> e.getUpdatedAt() != null)
                .max(Comparator.comparing(InfoEntity::getUpdatedAt))
                .ifPresent(e -> label.setText("Diperbarui: " + e.getUpdatedAt().format(FMT)));
    }

    private String kategoriLabel(String k) {
        return switch (k) {
            case "IDENTITAS"       -> "Identitas Laundry";
            case "LOKASI_KONTAK"   -> "Lokasi & Kontak";
            case "JAM_OPERASIONAL" -> "Jam Operasional";
            default -> k;
        };
    }

    private String kunciLabel(String k) {
        return switch (k) {
            case "nama_usaha"    -> "Nama Usaha";
            case "pemilik"       -> "Pemilik";
            case "tahun_berdiri" -> "Tahun Berdiri";
            case "alamat"        -> "Alamat";
            case "whatsapp"      -> "No. WhatsApp";
            case "instagram"     -> "Instagram";
            case "senin_jumat"   -> "Senin-Jumat";
            case "sabtu_minggu"  -> "Sabtu-Minggu";
            case "hari_libur"    -> "Hari Libur Nasional";
            default -> k;
        };
    }
}

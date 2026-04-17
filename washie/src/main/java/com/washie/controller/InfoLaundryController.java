package com.washie.controller;

import com.washie.model.InfoEntity;
import com.washie.service.InfoService;
import com.washie.util.SceneManager;
import com.washie.util.SessionManager;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import org.springframework.stereotype.Controller;

import java.net.URL;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.ResourceBundle;

@Controller
public class InfoLaundryController implements Initializable {

    // Identitas
    @FXML private Label lblNamaUsaha;
    @FXML private Label lblPemilik;
    @FXML private Label lblTahunBerdiri;
    @FXML private Label lblUpdatedIdentitas;

    // Lokasi
    @FXML private Label lblAlamat;
    @FXML private Label lblWa;
    @FXML private Label lblIg;
    @FXML private Label lblUpdatedLokasi;

    // Jam
    @FXML private Label lblSenJum;
    @FXML private Label lblSabMinggu;
    @FXML private Label lblLibur;
    @FXML private Label lblUpdatedJam;

    @FXML private Label lblAdminName;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("d MMM yyyy");

    private final InfoService infoService;
    private final SessionManager sessionManager;
    private final SceneManager sceneManager;

    public InfoLaundryController(InfoService infoService,
                                 SessionManager sessionManager,
                                 SceneManager sceneManager) {
        this.infoService = infoService;
        this.sessionManager = sessionManager;
        this.sceneManager = sceneManager;
    }

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        lblAdminName.setText(sessionManager.getCurrentUser().getNamaLengkap());
        loadData();
    }

    private void loadData() {
        setValue(lblNamaUsaha, "IDENTITAS", "nama_usaha");
        setValue(lblPemilik, "IDENTITAS", "pemilik");
        setValue(lblTahunBerdiri, "IDENTITAS", "tahun_berdiri");
        setUpdated(lblUpdatedIdentitas, "IDENTITAS");

        setValue(lblAlamat, "LOKASI_KONTAK", "alamat");
        setValue(lblWa, "LOKASI_KONTAK", "whatsapp");
        setValue(lblIg, "LOKASI_KONTAK", "instagram");
        setUpdated(lblUpdatedLokasi, "LOKASI_KONTAK");

        setValue(lblSenJum, "JAM_OPERASIONAL", "senin_jumat");
        setValue(lblSabMinggu, "JAM_OPERASIONAL", "sabtu_minggu");
        setValue(lblLibur, "JAM_OPERASIONAL", "hari_libur");
        setUpdated(lblUpdatedJam, "JAM_OPERASIONAL");
    }

    private void setValue(Label label, String kategori, String kunci) {
        infoService.getNilai(kategori, kunci).ifPresent(label::setText);
    }

    private void setUpdated(Label label, String kategori) {
        List<InfoEntity> list = infoService.getByKategori(kategori);
        list.stream()
                .filter(e -> e.getUpdatedAt() != null)
                .max((a, b) -> a.getUpdatedAt().compareTo(b.getUpdatedAt()))
                .ifPresent(e -> label.setText("Diperbarui: " + e.getUpdatedAt().format(FMT)));
    }

    @FXML
    private void handleEditIdentitas() { showEditDialog("IDENTITAS"); }

    @FXML
    private void handleEditLokasi() { showEditDialog("LOKASI_KONTAK"); }

    @FXML
    private void handleEditJam() { showEditDialog("JAM_OPERASIONAL"); }

    private void showEditDialog(String kategori) {
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
            lbl.setStyle("-fx-font-weight: bold;");
            fields[i] = new TextField(e.getNilai());
            fields[i].setPrefWidth(280);
            grid.add(lbl, 0, i);
            grid.add(fields[i], 1, i);
        }

        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dialog.setResultConverter(btn -> btn == ButtonType.OK);

        dialog.showAndWait().ifPresent(ok -> {
            if (!ok) return;
            for (int i = 0; i < items.size(); i++) {
                InfoEntity e = items.get(i);
                infoService.saveOrUpdate(e.getKategori(), e.getKunci(), fields[i].getText().trim());
            }
            loadData();
        });
    }

    private String kategoriLabel(String k) {
        return switch (k) {
            case "IDENTITAS" -> "Identitas Laundry";
            case "LOKASI_KONTAK" -> "Lokasi & Kontak";
            case "JAM_OPERASIONAL" -> "Jam Operasional";
            default -> k;
        };
    }

    private String kunciLabel(String k) {
        return switch (k) {
            case "nama_usaha" -> "Nama Usaha";
            case "pemilik" -> "Pemilik";
            case "tahun_berdiri" -> "Tahun Berdiri";
            case "alamat" -> "Alamat";
            case "whatsapp" -> "No. WhatsApp";
            case "instagram" -> "Instagram";
            case "senin_jumat" -> "Senin-Jumat";
            case "sabtu_minggu" -> "Sabtu-Minggu";
            case "hari_libur" -> "Hari Libur Nasional";
            default -> k;
        };
    }

    @FXML private void handleGoDashboard() {
        try { sceneManager.switchTo("/com/washie/view/AdminDashboardView.fxml"); }
        catch (Exception e) { e.printStackTrace(); }
    }

    @FXML private void handleGoLayanan() {
        try { sceneManager.switchTo("/com/washie/view/KelolaLayananView.fxml"); }
        catch (Exception e) { e.printStackTrace(); }
    }

    @FXML private void handleKeluar() {
        try {
            sessionManager.logout();
            sceneManager.switchTo("/com/washie/view/LoginView.fxml");
        } catch (Exception e) { e.printStackTrace(); }
    }
}

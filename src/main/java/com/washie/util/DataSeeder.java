package com.washie.util;

import com.washie.model.User;
import com.washie.repository.InfoRepository;
import com.washie.repository.LayananRepository;
import com.washie.repository.UserRepository;
import com.washie.model.InfoEntity;
import com.washie.model.Layanan;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
public class DataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final LayananRepository layananRepository;
    private final InfoRepository infoRepository;

    public DataSeeder(UserRepository u, LayananRepository l, InfoRepository i) {
        this.userRepository = u;
        this.layananRepository = l;
        this.infoRepository = i;
    }

    @Override
    public void run(String... args) {
        seedAdmin();
        seedLayanan();
        seedAddon();
        seedInfoLaundry();
        seedExpressConfig();
    }

    private void seedAdmin() {
        if (!userRepository.existsByNamaLengkap("Admin 1")) {
            User admin = new User("Admin 1", "081234567890", "admin123", User.Role.ADMIN);
            userRepository.save(admin);
        }
    }

    private void seedLayanan() {
        long layananCount = layananRepository.findAll().stream()
                .filter(l -> l.getTipe() == Layanan.Tipe.LAYANAN).count();
        if (layananCount == 0) {
            layananRepository.save(new Layanan("Cuci + Setrika",              7000d, "3 Hari Kerja",   true,  true));
            layananRepository.save(new Layanan("Cuci Kering",                 5000d, "2 Hari Kerja",   true,  true));
            layananRepository.save(new Layanan("Setrika Saja",                4000d, "1 Hari Kerja",   true,  true));
            layananRepository.save(new Layanan("Dry Cleaning",               20000d, "3-5 Hari Kerja", false, true));
            layananRepository.save(new Layanan("Cuci Handuk",                 4000d, "1-2 Hari Kerja", false, true));
            layananRepository.save(new Layanan("Cuci Karpet",                12000d, "2-3 Hari Kerja", false, true));
            layananRepository.save(new Layanan("Cuci Bedcover",              20000d, "2-3 Hari Kerja", false, false));
            layananRepository.save(new Layanan("Cuci Sprei & Sarung Bantal", 15000d, "2 Hari Kerja",   false, false));
            layananRepository.save(new Layanan("Cuci Selimut",               15000d, "2-3 Hari Kerja", true,  false));
            layananRepository.save(new Layanan("Cuci Gorden & Vitrase",      18000d, "3-4 Hari Kerja", false, false));
            layananRepository.save(new Layanan("Cuci Boneka",                15000d, "2-3 Hari Kerja", false, false));
        }
    }

    private void seedAddon() {
        long addonCount = layananRepository.findAll().stream()
                .filter(l -> l.getTipe() == Layanan.Tipe.ADDON).count();
        if (addonCount == 0) {
            // constructor: (nama, harga) → tipe otomatis ADDON
            layananRepository.save(new Layanan("Pewangi Premium",              3000d));
            layananRepository.save(new Layanan("Extra Softener",               2000d));
            layananRepository.save(new Layanan("Anti-Septik",                  2000d));
        }
    }

    private static final String KATEGORI_IDENTITAS = "IDENTITAS";
    private static final String KATEGORI_LOKASI = "LOKASI_KONTAK";
    private static final String KATEGORI_JAM = "JAM_OPERASIONAL";

    private void seedInfoLaundry() {
        if (infoRepository.findByKategori(KATEGORI_IDENTITAS).isEmpty()) {
            List<InfoEntity> infoList = List.of(
                    new InfoEntity(KATEGORI_IDENTITAS, "nama_usaha", "Washie Laundry"),
                    new InfoEntity(KATEGORI_IDENTITAS, "pemilik", "Bapak Agung Prayono"),
                    new InfoEntity(KATEGORI_IDENTITAS, "tahun_berdiri", "2020"),
                    new InfoEntity(KATEGORI_LOKASI, "alamat", "Jl. Kusbini No. 08, Yogyakarta"),
                    new InfoEntity(KATEGORI_LOKASI, "whatsapp", "0852-3456-7890"),
                    new InfoEntity(KATEGORI_LOKASI, "instagram", "@washie.laundry"),
                    new InfoEntity(KATEGORI_JAM, "senin_jumat", "08.00 - 21.00"),
                    new InfoEntity(KATEGORI_JAM, "sabtu_minggu", "09.00 - 19.00"),
                    new InfoEntity(KATEGORI_JAM, "hari_libur", "Tutup")
            );
            infoRepository.saveAll(infoList);
        }
    }

    private void seedExpressConfig() {
        if (infoRepository.findByKategori("ADDON_CONFIG").isEmpty()) {
            infoRepository.save(new InfoEntity("ADDON_CONFIG", "express_surcharge_per_kg", "5000"));
            infoRepository.save(new InfoEntity("ADDON_CONFIG", "express_surcharge_flat",   "15000"));
        }
    }
}


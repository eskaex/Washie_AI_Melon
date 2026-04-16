package com.washie.util;

import com.washie.model.User;
import com.washie.repository.InfoRepository;
import com.washie.repository.LayananRepository;
import com.washie.repository.UserRepository;
import com.washie.model.InfoEntity;
import com.washie.model.Layanan;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class DataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final LayananRepository layananRepository;
    private final InfoRepository infoRepository;

    public DataSeeder(UserRepository userRepo, LayananRepository layananRepo, InfoRepository infoRepo) {
        this.userRepository = userRepo;
        this.layananRepository = layananRepo;
        this.infoRepository = infoRepo;
    }

    @Override
    public void run(String... args) {
        seedAdmin();
        seedLayanan();
        seedInfo();
    }

    private void seedAdmin() {
        if (!userRepository.existsByNamaLengkap("Admin 1")) {
            User admin = new User("Admin 1", "081234567890", "admin123", User.Role.ADMIN);
            userRepository.save(admin);
        }
    }

    private void seedLayanan() {
        if (layananRepository.count() == 0) {
            layananRepository.save(new Layanan("Cuci Kering", 5000.0, "2 Hari Kerja"));
            layananRepository.save(new Layanan("Cuci + Setrika", 7000.0, "3 Hari Kerja"));
            layananRepository.save(new Layanan("Express (1 Hari)", 12000.0, "1 Hari Kerja"));
        }
    }

    private void seedInfo() {
        if (infoRepository.count() == 0) {
            // Identitas
            infoRepository.save(new InfoEntity("IDENTITAS", "nama_usaha", "Washie Laundry"));
            infoRepository.save(new InfoEntity("IDENTITAS", "pemilik", "Bapak Agung Prayono"));
            infoRepository.save(new InfoEntity("IDENTITAS", "tahun_berdiri", "2020"));
            // Lokasi & Kontak
            infoRepository.save(new InfoEntity("LOKASI_KONTAK", "alamat", "Jl. Kusbini No. 08, Yogyakarta"));
            infoRepository.save(new InfoEntity("LOKASI_KONTAK", "whatsapp", "0852-3456-7890"));
            infoRepository.save(new InfoEntity("LOKASI_KONTAK", "instagram", "@washie.laundry"));
            // Jam Operasional
            infoRepository.save(new InfoEntity("JAM_OPERASIONAL", "senin_jumat", "08.00 - 21.00"));
            infoRepository.save(new InfoEntity("JAM_OPERASIONAL", "sabtu_minggu", "09.00 - 19.00"));
            infoRepository.save(new InfoEntity("JAM_OPERASIONAL", "hari_libur", "Tutup"));
        }
    }
}

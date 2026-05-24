package com.washie.service;

import com.washie.model.Pesanan;
import com.washie.model.User;
import com.washie.repository.PesananRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class PesananService {

    private final PesananRepository pesananRepository;

    public PesananService(PesananRepository pesananRepository) {
        this.pesananRepository = pesananRepository;
    }

    public List<Pesanan> getAllPesanan() {
        return pesananRepository.findAll();
    }

    public List<Pesanan> getPesananTerkini() {
        return pesananRepository.findTop10ByOrderByUpdatedAtDesc();
    }

    public List<Pesanan> getPesananByUser(User user) {
        return pesananRepository.findByUser(user);
    }

    public Optional<Pesanan> getByKode(String kode) {
        return pesananRepository.findByKodePesanan(kode.toUpperCase().trim());
    }

    public Pesanan updateStatus(Long id, Pesanan.Status status) {
        Pesanan pesanan = pesananRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Pesanan tidak ditemukan"));
        pesanan.setStatus(status);
        return pesananRepository.save(pesanan);
    }

    public Pesanan save(Pesanan pesanan) {
        if (pesanan.getKodePesanan() == null) {
            pesanan.setKodePesanan(generateKode());
        }
        if (pesanan.getTanggalMasuk() == null) {
            pesanan.setTanggalMasuk(LocalDate.now());
        }
        if (pesanan.getBeratKg() != null && pesanan.getLayanan() != null) {
            pesanan.setTotalHarga(pesanan.getBeratKg() * pesanan.getLayanan().getHarga());
        }
        return pesananRepository.save(pesanan);
    }

    public List<Pesanan> cariPesanan(String keyword) {
        return pesananRepository
                .findByKodePesananContainingIgnoreCaseOrUserNamaLengkapContainingIgnoreCase(keyword, keyword);
    }

    public long countByStatus(Pesanan.Status status) {
        return pesananRepository.countByStatus(status);
    }

    private String generateKode() {
        long total = pesananRepository.count() + 1;
        return String.format("WS-%03d", total);
    }
}

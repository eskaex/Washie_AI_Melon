package com.washie.service;

import com.washie.model.Layanan;
import com.washie.repository.LayananRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class LayananService {

    private final LayananRepository layananRepository;

    public LayananService(LayananRepository layananRepository) {
        this.layananRepository = layananRepository;
    }

    public List<Layanan> getAllLayanan() {
        return layananRepository.findAll();
    }

    public List<Layanan> getLayananAktif() {
        return layananRepository.findByStatus(Layanan.Status.AKTIF);
    }

    public Optional<Layanan> getById(Long id) {
        return layananRepository.findById(id);
    }

    public Layanan save(Layanan layanan) {
        return layananRepository.save(layanan);
    }

    public Layanan tambahLayanan(String nama, Double harga, String estimasi) {
        Layanan layanan = new Layanan(nama, harga, estimasi);
        return layananRepository.save(layanan);
    }

    public void hapusLayanan(Long id) {
        layananRepository.deleteById(id);
    }

    public List<Layanan> cariLayanan(String keyword) {
        return layananRepository.findByNamaLayananContainingIgnoreCase(keyword);
    }
}
package com.washie.service;

import com.washie.model.Layanan;
import com.washie.repository.LayananRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Transactional
public class LayananService {

    private final LayananRepository layananRepository;

    public LayananService(LayananRepository layananRepository) {
        this.layananRepository = layananRepository;
    }

    public List<Layanan> getAll() {
        return layananRepository.findAll();
    }

    public List<Layanan> getLayananAktif() {
        return layananRepository.findByStatus(com.washie.model.Layanan.Status.AKTIF)
                .stream().filter(l -> l.getTipe() == com.washie.model.Layanan.Tipe.LAYANAN)
                .collect(Collectors.toList());
    }

    public List<Layanan> getAddonAktif() {
        return layananRepository.findByStatus(Layanan.Status.AKTIF)
                .stream().filter(l -> l.getTipe() == Layanan.Tipe.ADDON)
                .collect(Collectors.toList());
    }

    public Optional<Layanan> getById(Long id) {
        return layananRepository.findById(id);
    }

    public Layanan save(Layanan layanan) {
        return layananRepository.save(layanan);
    }

    public Optional<Layanan> cariSatuLayanan(String term) {
        List<Layanan> aktif = getLayananAktif();
        String kw = term.toLowerCase();
        for (Layanan l : aktif)
            if (l.getNamaLayanan().equalsIgnoreCase(term)) return Optional.of(l);
        for (Layanan l : aktif)
            if (l.getNamaLayanan().toLowerCase().contains(kw)) return Optional.of(l);
        for (String word : kw.split("\\s+")) {
            if (word.length() < 3) continue;
            for (Layanan l : aktif)
                if (l.getNamaLayanan().toLowerCase().contains(word)) return Optional.of(l);
        }
        return Optional.empty();
    }

    public void hapus(Long id) {
        layananRepository.deleteById(id);
    }

    public List<Layanan> cari(String keyword) {
        return layananRepository.findByNamaLayananContainingIgnoreCase(keyword);
    }
}
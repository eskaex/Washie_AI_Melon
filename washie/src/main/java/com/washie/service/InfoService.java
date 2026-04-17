package com.washie.service;

import com.washie.model.InfoEntity;
import com.washie.repository.InfoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class InfoService {

    private final InfoRepository infoRepository;

    public InfoService(InfoRepository infoRepository) {
        this.infoRepository = infoRepository;
    }

    public List<InfoEntity> getByKategori(String kategori) {
        return infoRepository.findByKategori(kategori);
    }

    public Optional<String> getNilai(String kategori, String kunci) {
        return infoRepository.findByKategoriAndKunci(kategori, kunci)
                .map(InfoEntity::getNilai);
    }

    public InfoEntity saveOrUpdate(String kategori, String kunci, String nilai) {
        Optional<InfoEntity> existing = infoRepository.findByKategoriAndKunci(kategori, kunci);
        InfoEntity entity = existing.orElse(new InfoEntity(kategori, kunci, nilai));
        entity.setNilai(nilai);
        return infoRepository.save(entity);
    }

    public List<InfoEntity> getAll() {
        return infoRepository.findAll();
    }
}
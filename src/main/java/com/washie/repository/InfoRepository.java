package com.washie.repository;

import com.washie.model.InfoEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InfoRepository extends JpaRepository<InfoEntity, Long> {
    List<InfoEntity> findByKategori(String kategori);
    Optional<InfoEntity> findByKategoriAndKunci(String kategori, String kunci);
}

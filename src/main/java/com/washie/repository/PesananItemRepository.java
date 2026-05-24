package com.washie.repository;

import com.washie.model.Pesanan;
import com.washie.model.PesananItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PesananItemRepository extends JpaRepository<PesananItem, Long> {
    List<PesananItem> findByPesananOrderByCreatedAtAsc(Pesanan pesanan);
}

package com.washie.repository;

import com.washie.model.Pesanan;
import com.washie.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PesananRepository extends JpaRepository<Pesanan, Long> {
    List<Pesanan> findByUser(User user);
    List<Pesanan> findByUserOrderByIdDesc(User user);
    Optional<Pesanan> findByKodePesanan(String kodePesanan);
    List<Pesanan> findByStatus(Pesanan.Status status);
    List<Pesanan> findByKodePesananContainingIgnoreCaseOrUserNamaLengkapContainingIgnoreCase(
            String kode, String nama);

    @Query("SELECT COUNT(p) FROM Pesanan p WHERE p.status = :status")
    long countByStatus(@Param("status") Pesanan.Status status);

    List<Pesanan> findTop10ByOrderByUpdatedAtDesc();
}
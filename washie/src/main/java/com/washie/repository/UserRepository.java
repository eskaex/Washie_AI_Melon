package com.washie.repository;

import com.washie.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByNamaLengkap(String namaLengkap);
    Optional<User> findByNamaLengkapAndPassword(String namaLengkap, String password);
    boolean existsByNamaLengkap(String namaLengkap);
    boolean existsByNoTelepon(String noTelepon);
}

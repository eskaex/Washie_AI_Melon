package com.washie.service;

import com.washie.model.User;
import com.washie.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@Transactional
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public Optional<User> login(String namaLengkap, String password) {
        return userRepository.findByNamaLengkapAndPassword(namaLengkap, password);
    }

    public User register(String namaLengkap, String noTelepon, String password) {
        if (userRepository.existsByNamaLengkap(namaLengkap)) {
            throw new IllegalArgumentException("Nama lengkap sudah terdaftar.");
        }
        if (noTelepon != null && userRepository.existsByNoTelepon(noTelepon)) {
            throw new IllegalArgumentException("Nomor telepon sudah terdaftar.");
        }
        User user = new User(namaLengkap, noTelepon, password, User.Role.USER);
        return userRepository.save(user);
    }

    public boolean isNamaExists(String namaLengkap) {
        return userRepository.existsByNamaLengkap(namaLengkap);
    }

    public boolean isNoTelpExists(String noTelepon) {
        return userRepository.existsByNoTelepon(noTelepon);
    }
}

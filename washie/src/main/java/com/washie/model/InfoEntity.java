package com.washie.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "info_laundry")
public class InfoEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "kategori", nullable = false)
    private String kategori;

    @Column(name = "kunci", nullable = false)
    private String kunci;

    @Column(name = "nilai", nullable = false, length = 500)
    private String nilai;

    @Column(name = "updatedAt")
    private LocalDateTime updatedAt;

    public InfoEntity() {}

    public InfoEntity(String kategori, String kunci, String nilai) {
        this.kategori = kategori;
        this.kunci = kunci;
        this.nilai = nilai;
    }

    @PrePersist @PreUpdate
    protected void onUpdate() { updatedAt = LocalDateTime.now(); }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getKategori() { return kategori; }
    public void setKategori(String kategori) { this.kategori = kategori; }
    public String getKunci() { return kunci; }
    public void setKunci(String kunci) { this.kunci = kunci; }
    public String getNilai() { return nilai; }
    public void setNilai(String nilai) { this.nilai = nilai; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}

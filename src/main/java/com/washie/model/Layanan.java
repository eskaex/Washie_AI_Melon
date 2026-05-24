package com.washie.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "layanan")
public class Layanan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID_Layanan")
    private Long idLayanan;

    @Column(name = "namaLayanan", nullable = false)
    private String namaLayanan;

    @Column(name = "Harga", nullable = false)
    private Double harga;

    @Column(name = "estimasiWaktu")
    private String estimasiWaktu;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private Status status = Status.AKTIF;

    @Column(name = "updatedAt")
    private LocalDateTime updatedAt;

    public Layanan() {}

    public Layanan(String namaLayanan, Double harga, String estimasiWaktu) {
        this.namaLayanan = namaLayanan;
        this.harga = harga;
        this.estimasiWaktu = estimasiWaktu;
        this.status = Status.AKTIF;
    }

    @PrePersist @PreUpdate
    protected void onUpdate() { updatedAt = LocalDateTime.now(); }

    public Long getIdLayanan() { return idLayanan; }
    public void setIdLayanan(Long idLayanan) { this.idLayanan = idLayanan; }
    public String getNamaLayanan() { return namaLayanan; }
    public void setNamaLayanan(String namaLayanan) { this.namaLayanan = namaLayanan; }
    public Double getHarga() { return harga; }
    public void setHarga(Double harga) { this.harga = harga; }
    public String getEstimasiWaktu() { return estimasiWaktu; }
    public void setEstimasiWaktu(String estimasiWaktu) { this.estimasiWaktu = estimasiWaktu; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public enum Status { AKTIF, NONAKTIF }
}

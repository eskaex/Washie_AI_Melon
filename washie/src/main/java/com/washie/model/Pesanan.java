package com.washie.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "pesanan")
public class Pesanan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID_Pesanan")
    private Long idPesanan;

    @Column(name = "kodePesanan", unique = true)
    private String kodePesanan;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "ID_User", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "ID_Layanan", nullable = false)
    private com.washie.model.Layanan layanan;

    @Column(name = "tanggalMasuk")
    private LocalDate tanggalMasuk;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private Status status = Status.DIPROSES;

    @Column(name = "beratKg")
    private Double beratKg;

    @Column(name = "totalHarga")
    private Double totalHarga;

    @Column(name = "updatedAt")
    private LocalDateTime updatedAt;

    public Pesanan() {}

    @PrePersist @PreUpdate
    protected void onUpdate() { updatedAt = LocalDateTime.now(); }

    public Long getIdPesanan() { return idPesanan; }
    public void setIdPesanan(Long idPesanan) { this.idPesanan = idPesanan; }
    public String getKodePesanan() { return kodePesanan; }
    public void setKodePesanan(String kodePesanan) { this.kodePesanan = kodePesanan; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public com.washie.model.Layanan getLayanan() { return layanan; }
    public void setLayanan(com.washie.model.Layanan layanan) { this.layanan = layanan; }
    public LocalDate getTanggalMasuk() { return tanggalMasuk; }
    public void setTanggalMasuk(LocalDate tanggalMasuk) { this.tanggalMasuk = tanggalMasuk; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public Double getBeratKg() { return beratKg; }
    public void setBeratKg(Double beratKg) { this.beratKg = beratKg; }
    public Double getTotalHarga() { return totalHarga; }
    public void setTotalHarga(Double totalHarga) { this.totalHarga = totalHarga; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public enum Status { DIPROSES, SELESAI, DIAMBIL }
}

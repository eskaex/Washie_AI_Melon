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

    /** Estimasi waktu, hanya relevan untuk LAYANAN utama */
    @Column(name = "estimasiWaktu")
    private String estimasiWaktu;

    /** LAYANAN = layanan utama, ADDON = produk tambahan */
    @Enumerated(EnumType.STRING)
    @Column(name = "tipe")
    private Tipe tipe = Tipe.LAYANAN;

    /** Apakah mendukung Express (hanya relevan untuk LAYANAN) */
    @Column(name = "bisaExpress")
    private boolean bisaExpress = false;

    /** true = harga per kg, false = per item/flat */
    @Column(name = "perKg")
    private boolean perKg = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private Status status = Status.AKTIF;

    @Column(name = "updatedAt")
    private LocalDateTime updatedAt;

    public Layanan() {}

    /** Constructor untuk layanan utama */
    public Layanan(String namaLayanan, Double harga, String estimasiWaktu,
                   boolean bisaExpress, boolean perKg) {
        this.namaLayanan   = namaLayanan;
        this.harga         = harga;
        this.estimasiWaktu = estimasiWaktu;
        this.bisaExpress   = bisaExpress;
        this.perKg         = perKg;
        this.tipe          = Tipe.LAYANAN;
        this.status        = Status.AKTIF;
    }

    /** Constructor untuk addon */
    public Layanan(String namaLayanan, Double harga) {
        this.namaLayanan = namaLayanan;
        this.harga       = harga;
        this.tipe        = Tipe.ADDON;
        this.perKg       = false;
        this.bisaExpress = false;
        this.status      = Status.AKTIF;
    }

    @PrePersist @PreUpdate
    protected void onUpdate() { updatedAt = LocalDateTime.now(); }

    // ── Getters & Setters ──────────────────────────────────────────────────
    public Long getIdLayanan()               { return idLayanan; }
    public void setIdLayanan(Long v)         { this.idLayanan = v; }
    public String getNamaLayanan()           { return namaLayanan; }
    public void setNamaLayanan(String v)     { this.namaLayanan = v; }
    public Double getHarga()                 { return harga; }
    public void setHarga(Double v)           { this.harga = v; }
    public String getEstimasiWaktu()         { return estimasiWaktu; }
    public void setEstimasiWaktu(String v)   { this.estimasiWaktu = v; }
    public Tipe getTipe()                    { return tipe; }
    public void setTipe(Tipe v)              { this.tipe = v; }
    public boolean isBisaExpress()           { return bisaExpress; }
    public void setBisaExpress(boolean v)    { this.bisaExpress = v; }
    public boolean isPerKg()                 { return perKg; }
    public void setPerKg(boolean v)          { this.perKg = v; }
    public Status getStatus()                { return status; }
    public void setStatus(Status v)          { this.status = v; }
    public LocalDateTime getUpdatedAt()      { return updatedAt; }
    public void setUpdatedAt(LocalDateTime v){ this.updatedAt = v; }

    public enum Tipe   { LAYANAN, ADDON }
    public enum Status { AKTIF, NONAKTIF }
}

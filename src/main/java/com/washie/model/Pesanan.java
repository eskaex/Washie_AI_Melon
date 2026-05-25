package com.washie.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

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

    @Column(name = "totalHarga")
    private Double totalHarga;

    @OneToMany(mappedBy = "pesanan", cascade = CascadeType.ALL,
            fetch = FetchType.EAGER, orphanRemoval = true)
    private List<PesananItem> items = new ArrayList<>();

    @Column(name = "updatedAt")
    private LocalDateTime updatedAt;

    public Pesanan() {}

    @PrePersist @PreUpdate
    protected void onUpdate() { updatedAt = LocalDateTime.now(); }

    public Long getIdPesanan()              { return idPesanan; }
    public void setIdPesanan(Long v)        { this.idPesanan = v; }
    public String getKodePesanan()          { return kodePesanan; }
    public void setKodePesanan(String v)    { this.kodePesanan = v; }
    public User getUser()                   { return user; }
    public void setUser(User v)             { this.user = v; }
    public Layanan getLayanan()             { return layanan; }
    public void setLayanan(Layanan v)       { this.layanan = v; }
    public LocalDate getTanggalMasuk()      { return tanggalMasuk; }
    public void setTanggalMasuk(LocalDate v){ this.tanggalMasuk = v; }
    public Status getStatus()               { return status; }
    public void setStatus(Status v)         { this.status = v; }
    public Double getTotalHarga()           { return totalHarga; }
    public void setTotalHarga(Double v)     { this.totalHarga = v; }
    public List<PesananItem> getItems()     { return items; }
    public void setItems(List<PesananItem> v){ this.items = v; }
    public LocalDateTime getUpdatedAt()     { return updatedAt; }
    public void setUpdatedAt(LocalDateTime v){ this.updatedAt = v; }

    public enum Status {
        BELUM_DIPROSES,  // Baru masuk
        DIPROSES,        // Sedang dicuci
        SELESAI,         // Selesai, siap diambil
        DIAMBIL,         // Sudah diambil user
        DIBATALKAN       // Dibatalkan oleh user/admin
    }
}

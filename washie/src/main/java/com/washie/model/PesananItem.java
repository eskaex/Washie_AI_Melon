package com.washie.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "pesanan_item")
public class PesananItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID_PesananItem")
    private Long idPesananItem;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ID_Pesanan", nullable = false)
    private Pesanan pesanan;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "ID_Layanan", nullable = false)
    private Layanan layanan;

    /** Berat dalam kg (untuk layanan per-kg), null jika per-item */
    @Column(name = "beratKg")
    private Double beratKg;

    /** Jumlah item (untuk layanan per-item), null jika per-kg */
    @Column(name = "jumlahItem")
    private Integer jumlahItem;

    /** STANDAR atau EXPRESS */
    @Column(name = "kecepatan")
    private String kecepatan;

    /** Biaya express untuk item ini saja */
    @Column(name = "expressTotal")
    private Double expressTotal;

    /** Nama-nama addon yang dipilih untuk item ini, dipisah koma */
    @Column(name = "addonNama", length = 500)
    private String addonNama;

    /** Total harga addon untuk item ini */
    @Column(name = "addonTotal")
    private Double addonTotal;

    /** Subtotal layanan saja (belum termasuk express dan addon) */
    @Column(name = "subtotalLayanan")
    private Double subtotalLayanan;

    /** Total item ini (subtotalLayanan + expressTotal + addonTotal) */
    @Column(name = "totalItem")
    private Double totalItem;

    @Column(name = "createdAt")
    private LocalDateTime createdAt;

    public PesananItem() {}

    @PrePersist
    protected void onCreate() { createdAt = LocalDateTime.now(); }

    // ── Getters & Setters ──────────────────────────────────────────────────
    public Long getIdPesananItem()             { return idPesananItem; }
    public void setIdPesananItem(Long v)       { this.idPesananItem = v; }
    public Pesanan getPesanan()                { return pesanan; }
    public void setPesanan(Pesanan v)          { this.pesanan = v; }
    public Layanan getLayanan()                { return layanan; }
    public void setLayanan(Layanan v)          { this.layanan = v; }
    public Double getBeratKg()                 { return beratKg; }
    public void setBeratKg(Double v)           { this.beratKg = v; }
    public Integer getJumlahItem()             { return jumlahItem; }
    public void setJumlahItem(Integer v)       { this.jumlahItem = v; }
    public String getKecepatan()               { return kecepatan; }
    public void setKecepatan(String v)         { this.kecepatan = v; }
    public Double getExpressTotal()            { return expressTotal; }
    public void setExpressTotal(Double v)      { this.expressTotal = v; }
    public String getAddonNama()               { return addonNama; }
    public void setAddonNama(String v)         { this.addonNama = v; }
    public Double getAddonTotal()              { return addonTotal; }
    public void setAddonTotal(Double v)        { this.addonTotal = v; }
    public Double getSubtotalLayanan()         { return subtotalLayanan; }
    public void setSubtotalLayanan(Double v)   { this.subtotalLayanan = v; }
    public Double getTotalItem()               { return totalItem; }
    public void setTotalItem(Double v)         { this.totalItem = v; }
    public LocalDateTime getCreatedAt()        { return createdAt; }
    public void setCreatedAt(LocalDateTime v)  { this.createdAt = v; }

    public String getRingkasan() {
        StringBuilder sb = new StringBuilder(layanan.getNamaLayanan());
        if (beratKg != null && beratKg > 0)
            sb.append(" x ").append(beratKg).append(" kg");
        else if (jumlahItem != null && jumlahItem > 0)
            sb.append(" x ").append(jumlahItem).append(" item");
        if ("EXPRESS".equals(kecepatan)) sb.append(" [Express]");
        return sb.toString();
    }
}

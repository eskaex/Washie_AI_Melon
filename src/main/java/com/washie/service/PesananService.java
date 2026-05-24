package com.washie.service;

import com.washie.model.*;
import com.washie.repository.PesananItemRepository;
import com.washie.repository.PesananRepository;
import com.washie.model.User;
import com.washie.util.SessionManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class PesananService {

    private final PesananRepository     pesananRepository;
    private final PesananItemRepository itemRepository;
    private final SessionManager        sessionManager;

    public PesananService(PesananRepository pesananRepository,
                          PesananItemRepository itemRepository,
                          SessionManager sessionManager) {
        this.pesananRepository = pesananRepository;
        this.itemRepository    = itemRepository;
        this.sessionManager    = sessionManager;
    }

    public List<Pesanan> getPesananTerkini() {
        return pesananRepository.findTop10ByOrderByUpdatedAtDesc();
    }

    public Optional<Pesanan> getByKode(String kode) {
        return pesananRepository.findByKodePesanan(kode.toUpperCase().trim());
    }

    public Pesanan updateStatus(Long id, Pesanan.Status status) {
        Pesanan p = pesananRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Pesanan tidak ditemukan"));
        p.setStatus(status);
        return pesananRepository.save(p);
    }

    public List<Pesanan> cariPesanan(String keyword) {
        return pesananRepository
                .findByKodePesananContainingIgnoreCaseOrUserNamaLengkapContainingIgnoreCase(
                        keyword, keyword);
    }

    public long countByStatus(Pesanan.Status status) {
        return pesananRepository.countByStatus(status);
    }

    public String simpanPesananDariChat(List<ItemDraft> drafts) {
        User currentUser = sessionManager.getCurrentUser();
        if (currentUser == null) throw new IllegalStateException("Tidak ada user yang login.");
        if (drafts == null || drafts.isEmpty()) throw new IllegalArgumentException("Tidak ada item.");

        Pesanan pesanan = new Pesanan();
        pesanan.setKodePesanan(generateKodePesanan());
        pesanan.setUser(currentUser);
        pesanan.setTanggalMasuk(LocalDate.now());
        pesanan.setStatus(Pesanan.Status.DIPROSES);
        pesanan.setLayanan(drafts.get(0).layanan); // layanan utama (item pertama)

        double grandTotal = 0;
        for (ItemDraft d : drafts) {
            PesananItem item = new PesananItem();
            item.setPesanan(pesanan);
            item.setLayanan(d.layanan);
            item.setBeratKg(d.layanan.isPerKg() && d.beratKg > 0 ? d.beratKg : null);
            item.setJumlahItem(!d.layanan.isPerKg() && d.jumlahItem > 0 ? d.jumlahItem : null);
            item.setKecepatan(d.kecepatan);
            item.setExpressTotal(d.expressTotal > 0 ? d.expressTotal : null);
            item.setAddonNama(d.addonNama.isBlank() ? null : d.addonNama);
            item.setAddonTotal(d.addonTotal > 0 ? d.addonTotal : null);
            item.setSubtotalLayanan(d.subtotalLayanan);
            item.setTotalItem(d.totalItem);
            pesanan.getItems().add(item);
            grandTotal += d.totalItem;
        }
        pesanan.setTotalHarga(grandTotal);

        Pesanan saved = pesananRepository.save(pesanan);
        return saved.getKodePesanan();
    }

    public String generateKodePesanan() {
        long total = pesananRepository.count() + 1;
        String kode = String.format("WS-%03d", total);
        while (pesananRepository.findByKodePesanan(kode).isPresent()) {
            total++;
            kode = String.format("WS-%03d", total);
        }
        return kode;
    }

    public List<Pesanan> getPesananByUser(User user) {
        return pesananRepository.findByUser(user);
    }

    // ── DTO ──────────────────────────────────────────────────────────────
    public static class ItemDraft {
        public Layanan layanan;
        public double  beratKg;
        public int     jumlahItem;
        public String  kecepatan;
        public double  expressTotal;
        public String  addonNama;
        public double  addonTotal;
        public double  subtotalLayanan;
        public double  totalItem;

        public ItemDraft(Layanan layanan, double beratKg, int jumlahItem,
                         String kecepatan, double expressTotal,
                         String addonNama, double addonTotal,
                         double subtotalLayanan, double totalItem) {
            this.layanan        = layanan;
            this.beratKg        = beratKg;
            this.jumlahItem     = jumlahItem;
            this.kecepatan      = kecepatan;
            this.expressTotal   = expressTotal;
            this.addonNama      = addonNama;
            this.addonTotal     = addonTotal;
            this.subtotalLayanan= subtotalLayanan;
            this.totalItem      = totalItem;
        }
    }
}

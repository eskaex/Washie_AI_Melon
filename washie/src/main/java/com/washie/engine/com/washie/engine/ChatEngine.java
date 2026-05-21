package com.washie.engine;

import com.washie.model.InfoEntity;
import com.washie.model.Layanan;
import com.washie.service.InfoService;
import com.washie.service.LayananService;
import com.washie.service.PesananService;
import com.washie.service.PesananService.ItemDraft;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.*;
import java.util.regex.*;
import java.util.stream.Collectors;

@Component
public class ChatEngine {

    private final LayananService layananService;
    private final PesananService pesananService;
    private final InfoService    infoService;

    public ChatEngine(LayananService l, PesananService p, InfoService i) {
        this.layananService = l; this.pesananService = p; this.infoService = i;
    }

        public ConvState state      = ConvState.IDLE;
        public Layanan layanan;
        public double beratKg    = 0;
        public int jumlahItem = 0;
        public String kecepatan;
        public double expressTotal = 0;
        public List<String> addonNama  = new ArrayList<>();
        public List<Double> addonHarga = new ArrayList<>();
        public List<ItemDraft> draftItems = new ArrayList<>();
    }

    public enum ConvState {
        IDLE,
        TANYA_BERAT,
        TANYA_JUMLAH,
        PILIH_KECEPATAN,
        PILIH_ADDON,
        TANYA_TAMBAH_ITEM,
        KONFIRMASI
    }

    private static final List<String[]> KEYWORD_MAP = new ArrayList<>();
    static {
        KEYWORD_MAP.add(new String[]{"cuci\\s*(\\+\\s*)?setrika|wash.*iron",             "Cuci + Setrika"});
        KEYWORD_MAP.add(new String[]{"cuci\\s*kering(?!\\s*setrika)|wash\\s*only",       "Cuci Kering"});
        KEYWORD_MAP.add(new String[]{"setrika\\s*(saja|aja|only|doank)|ironing\\s*only", "Setrika Saja"});
        KEYWORD_MAP.add(new String[]{"dry\\s*clean",                                     "Dry Cleaning"});
        KEYWORD_MAP.add(new String[]{"bedcover|bed\\s*cover",                            "Cuci Bedcover"});
        KEYWORD_MAP.add(new String[]{"sprei|sarung\\s*bantal",                           "Cuci Sprei"});
        KEYWORD_MAP.add(new String[]{"selimut|blanket",                                  "Cuci Selimut"});
        KEYWORD_MAP.add(new String[]{"handuk|towel",                                     "Cuci Handuk"});
        KEYWORD_MAP.add(new String[]{"gorden|gordyn|vitrase|tirai",                      "Cuci Gorden"});
        KEYWORD_MAP.add(new String[]{"karpet|carpet",                                    "Cuci Karpet"});
        KEYWORD_MAP.add(new String[]{"boneka|stuffed|teddy",                             "Cuci Boneka"});
    }

    private static final List<String[]> INFO_MAP = new ArrayList<>();
    static {
        String[] names = {"Cuci + Setrika","Cuci Kering","Setrika Saja","Dry Cleaning",
                "Cuci Bedcover","Cuci Sprei","Cuci Selimut","Cuci Handuk",
                "Cuci Gorden","Cuci Karpet","Cuci Boneka"};
        String[] regex = {"cuci.*setrika|cuci\\+setrika","cuci\\s*kering","setrika\\s*saja","dry\\s*clean",
                "bedcover","sprei","selimut","handuk","gorden","karpet","boneka"};
        for (int i = 0; i < names.length; i++) {
            INFO_MAP.add(new String[]{"(harga|biaya|tarif|berapa).*(" + regex[i] + ")",           "HARGA",    names[i]});
            INFO_MAP.add(new String[]{"(lama|estimasi|kapan|selesai|waktu).*(" + regex[i] + ")",  "ESTIMASI", names[i]});
            INFO_MAP.add(new String[]{"(apa|jelaskan|info|maksud|ceritakan).*(" + regex[i] + ")", "DESKRIPSI",names[i]});
        }
    }

    private static final String[] LOKASI_KW = {
            "di mana","dimana","lokasi","alamat","ada di mana","tempatnya", "serlok", "shareloc",
            "laundry di mana","laundry ada di","di mana laundry","washie di mana","di mana washie",
            "kontak","whatsapp","wa ","instagram","ig ","hubungi","nomor","no hp","nomer hp"
    };
    private static final String[] JAM_KW = {
            "jam buka","jam tutup","jam operasional","buka jam","tutup jam","jam berapa",
            "operasional","waktu buka","hari apa","buka hari","kapan buka",
            "hari senin","hari minggu","hari libur","buka sampai", "jam brp"
    };
    private static final String[] DAFTAR_KW = {
            "daftar layanan","list layanan","layanan apa","layanan ada apa","ada layanan apa",
            "apa saja layanan","layanan tersedia","pilihan layanan","menu layanan",
            "semua layanan","layanan yang ada","ada apa aja","apa aja layanan"
    };
    private static final String[] PENGUMUMAN_KW = {
            "pengumuman","info terbaru","ada info","ada promo","promo","info hari ini",
            "berita","update","announcement","informasi terbaru"
    };

    private static final Pattern ANGKA_PAT =
            Pattern.compile("(\\d+(?:[.,]\\d+)?)\\s*(?:kg|kilo(?:gram)?|item|pcs|buah|lembar|meter|helai)?",
                    Pattern.CASE_INSENSITIVE);

    public BotResponse process(String input, ChatSession session) {
        if (input == null || input.isBlank())
            return txt("Maaf, saya tidak mengerti. Coba ketik ulang ya.");
        String raw   = input.trim();
        String lower = raw.toLowerCase();

        return switch (session.state) {
            case IDLE -> handleIdle(raw, lower, session);
            case TANYA_BERAT -> handleTanyaBerat(raw, lower, session);
            case TANYA_JUMLAH -> handleTanyaJumlah(raw, lower, session);
            case PILIH_KECEPATAN -> handlePilihKecepatan(lower, session);
            case PILIH_ADDON -> handlePilihAddon(lower, session);
            case TANYA_TAMBAH_ITEM -> handleTanyaTambahItem(raw, lower, session);
            case KONFIRMASI -> handleKonfirmasi(lower, session);
        };
    }

    private BotResponse handleIdle(String raw, String lower, ChatSession session) {
        String kode = cariKode(raw);
        if (kode != null) return cekStatus(kode);
        if (has(lower,"status pesanan","cek pesanan","lacak pesanan", "info pesanan"))
            return txt("Masukkan kode pesanan kamu.\nContoh: cek WS-001");

        if (has(lower,"halo","hai","hello","hi ","selamat pagi","selamat siang",
                "selamat sore","selamat malam","hei","assalamu","permisi", "oy", "oi"))
            return salam();

        for (String kw : LOKASI_KW)    if (lower.contains(kw)) return respLokasi();
        for (String kw : JAM_KW)       if (lower.contains(kw)) return respJam();
        for (String kw : DAFTAR_KW)    if (lower.contains(kw)) return respDaftarLayanan();
        for (String kw : PENGUMUMAN_KW) if (lower.contains(kw)) return respPengumuman();

        for (String[] row : INFO_MAP)
            if (Pattern.compile(row[0], Pattern.CASE_INSENSITIVE).matcher(raw).find())
                return handleInfo(row[1], row[2]);

        for (String[] row : KEYWORD_MAP) {
            if (Pattern.compile(row[0], Pattern.CASE_INSENSITIVE).matcher(raw).find()) {
                Optional<Layanan> opt = cariLayananDb(row[1]);
                if (opt.isEmpty())
                    return txt("Maaf, layanan " + row[1] + " belum tersedia atau nonaktif.");
                return mulaiItem(opt.get(), raw, session);
            }
        }

        if (has(lower,"cuci baju","cuci pakaian","mau laundry","mau cuci",
                "pengen laundry","ingin laundry","laundry dong","laundry ya", "mau nyuci",
                "info laundry", "mau pesan"))
            return respTanyaLayanan();

        if (has(lower,"harga","tarif","biaya")) return respDaftarLayanan();

        if (has(lower,"terima kasih","makasih","thanks","thx","tq","mksih","terimakasih", "mksh"))
            return txt("Sama-sama! Senang bisa membantu.");

        return respFallback();
    }

    private BotResponse handleTanyaBerat(String raw, String lower, ChatSession session) {
        if (isBatal(lower)) { resetItem(session); return txt("Item dibatalkan. " + lanjutPrompt(session)); }
        double angka = extractAngka(raw);
        if (angka <= 0) return txt("Masukkan berat dalam kg.\nContoh: 2 kg  atau  1.5 kg");
        session.beratKg = angka;
        return lanjutSetelahKuantitas(session);
    }

    private BotResponse handleTanyaJumlah(String raw, String lower, ChatSession session) {
        if (isBatal(lower)) { resetItem(session); return txt("Item dibatalkan. " + lanjutPrompt(session)); }
        double angka = extractAngka(raw);
        if (angka <= 0 || angka != Math.floor(angka))
            return txt("Masukkan jumlah item (bilangan bulat).\nContoh: 1  atau  3 item");
        session.jumlahItem = (int) angka;
        return lanjutSetelahKuantitas(session);
    }

    private BotResponse lanjutSetelahKuantitas(ChatSession session) {
        if (session.layanan.isBisaExpress()) {
            session.state = ConvState.PILIH_KECEPATAN;
            return promptKecepatan(session);
        }
        session.kecepatan = "STANDAR";
        session.expressTotal = 0;
        session.state = ConvState.PILIH_ADDON;
        return promptAddon();
    }

    private BotResponse handlePilihKecepatan(String lower, ChatSession session) {
        if (isBatal(lower)) { resetItem(session); return txt("Item dibatalkan. " + lanjutPrompt(session)); }
        if (has(lower,"express","ekspres","cepat","kilat","1 hari","sehari")) {
            session.kecepatan    = "EXPRESS";
            session.expressTotal = hitungExpress(session);
            session.state        = ConvState.PILIH_ADDON;
            return promptAddon();
        }
        if (has(lower,"standar","standard","biasa","normal","reguler")) {
            session.kecepatan    = "STANDAR";
            session.expressTotal = 0;
            session.state        = ConvState.PILIH_ADDON;
            return promptAddon();
        }
        return promptKecepatan(session);
    }

    private BotResponse handlePilihAddon(String lower, ChatSession session) {
        if (isBatal(lower)) { resetItem(session); return txt("Item dibatalkan. " + lanjutPrompt(session)); }

        if (has(lower,"tidak","tidak perlu","no","gak","ga","tanpa","skip","langsung","udah","lanjut")) {
            return selesaiSatuItem(session);
        }

        List<Layanan> addons = layananService.getAddonAktif();
        boolean ada = false;

        if (has(lower,"keduanya","semua","all","both")) {
            for (Layanan a : addons)
                if (!session.addonNama.contains(a.getNamaLayanan())) {
                    session.addonNama.add(a.getNamaLayanan());
                    session.addonHarga.add(a.getHarga());
                    ada = true;
                }
        } else {
            for (Layanan a : addons) {
                if (session.addonNama.contains(a.getNamaLayanan())) continue;
                if (addonDipilih(a.getNamaLayanan().toLowerCase(), lower)) {
                    session.addonNama.add(a.getNamaLayanan());
                    session.addonHarga.add(a.getHarga());
                    ada = true;
                }
            }
        }

        if (ada) return selesaiSatuItem(session);

        return txt("Maaf, saya tidak mengenali pilihan tersebut.\n\n" + buatOpsiAddon() +
                "Ketik nama add-on, atau ketik tidak jika tidak perlu.");
    }

    private BotResponse selesaiSatuItem(ChatSession session) {
        double subtotal   = hitungSubtotal(session);
        double addonTotal = session.addonHarga.stream().mapToDouble(Double::doubleValue).sum();
        double totalItem  = subtotal + session.expressTotal + addonTotal;

        ItemDraft draft = new ItemDraft(
                session.layanan,
                session.beratKg,
                session.jumlahItem,
                session.kecepatan,
                session.expressTotal,
                String.join(", ", session.addonNama),
                addonTotal,
                subtotal,
                totalItem
        );
        session.draftItems.add(draft);

        resetItem(session);

        session.state = ConvState.TANYA_TAMBAH_ITEM;

        StringBuilder sb = new StringBuilder();
        sb.append("Item ditambahkan!\n\n");
        sb.append(ringkasanDraftSementara(session));
        sb.append("\nApakah ingin menambah layanan lagi?\n");
        sb.append("- Ketik ya / nama layanan → tambah item baru\n");
        sb.append("- Ketik selesai / tidak   → konfirmasi pesanan");
        return txt(sb.toString());
    }

    private BotResponse handleTanyaTambahItem(String raw, String lower, ChatSession session) {
        if (has(lower,"batal semua","cancel semua","batal pesanan")) {
            resetSemua(session);
            return txt("Semua pesanan dibatalkan. Ada yang bisa saya bantu?");
        }

        if (has(lower,"selesai","tidak","no","gak","ga","sudah","cukup","lanjut konfirmasi")) {
            session.state = ConvState.KONFIRMASI;
            return tampilNota(session);
        }

        String rawCheck = lower.replace("ya ","").replace("iya ","").trim();

        for (String[] row : KEYWORD_MAP) {
            if (Pattern.compile(row[0], Pattern.CASE_INSENSITIVE).matcher(raw).find()) {
                Optional<Layanan> opt = cariLayananDb(row[1]);
                if (opt.isPresent()) {
                    session.state = ConvState.IDLE;
                    return mulaiItem(opt.get(), raw, session);
                }
            }
        }

        if (has(lower,"ya","iya","yes","tambah","tambah lagi")) {
            session.state = ConvState.IDLE;
            return respTanyaLayanan();
        }

        return txt("Ketik nama layanan untuk menambah (misal: cuci kering 1 kg),\n" +
                "atau ketik selesai untuk konfirmasi pesanan.");
    }

    private BotResponse handleKonfirmasi(String lower, ChatSession session) {
        if (has(lower,"ya","iya","yes","ok","oke","betul","benar","konfirmasi","setuju","pesan"))
            return simpanPesanan(session);
        if (isBatal(lower)) {
            resetSemua(session);
            return txt("Pesanan dibatalkan. Ada yang bisa saya bantu?");
        }
        if (has(lower,"ubah","ganti","edit","salah","ulang")) {
            resetSemua(session);
            return txt("Oke, mari ulangi dari awal.\nKetik kembali kebutuhan laundry Anda.");
        }
        return txt("Mohon konfirmasi:\n" +
                "- Ketik ya    → simpan pesanan\n" +
                "- Ketik batal → batalkan\n" +
                "- Ketik ubah  → ulangi dari awal");
    }

    private BotResponse mulaiItem(Layanan layanan, String raw, ChatSession session) {
        session.layanan = layanan;
        double angka    = extractAngka(raw);

        if (layanan.isPerKg()) {
            if (angka > 0) { session.beratKg = angka; return lanjutSetelahKuantitas(session); }
            session.state = ConvState.TANYA_BERAT;
            return txt("Layanan " + layanan.getNamaLayanan() + " — Rp" + fmt(layanan.getHarga()) + "/kg\n\n" +
                    "Berapa berat pakaiannya? (dalam kg)\n" +
                    "Contoh: 2 kg  atau  1.5 kg");
        } else {
            if (angka > 0 && angka == Math.floor(angka)) {
                session.jumlahItem = (int) angka;
                return lanjutSetelahKuantitas(session);
            }
            session.state = ConvState.TANYA_JUMLAH;
            return txt("Layanan " + layanan.getNamaLayanan() + " — Rp" + fmt(layanan.getHarga()) + "/item\n\n" +
                    "Berapa jumlah item yang akan dicuci?\n" +
                    "Contoh: 1 item  atau  2");
        }
    }

    private BotResponse promptKecepatan(ChatSession session) {
        double rate   = getExpressRate(session.layanan);
        String satuan = session.layanan.isPerKg() ? "/kg" : "/item";
        return txt("Pilih kecepatan untuk " + session.layanan.getNamaLayanan() + ":\n\n" +
                "- Standar : " + session.layanan.getEstimasiWaktu() + "  (tidak ada biaya tambahan)\n" +
                "- Express : 1 Hari Kerja  (+Rp" + fmt(rate) + satuan + "," +
                " total surcharge Rp" + fmt(hitungExpress(session)) + ")\n\n" +
                "Ketik standar atau express");
    }

    private BotResponse promptAddon() {
        String opsi = buatOpsiAddon();
        if (opsi.isBlank())
            return txt("Tidak ada produk tambahan tersedia.\n\nKetik lanjut untuk melanjutkan.");
        return txt("Apakah ingin menambahkan produk tambahan?\n\n" + opsi +
                "Ketik nama produk, atau ketik tidak jika tidak perlu.");
    }

    private String buatOpsiAddon() {
        List<Layanan> addons = layananService.getAddonAktif();
        if (addons.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (Layanan a : addons)
            sb.append("- ").append(a.getNamaLayanan()).append("  +Rp").append(fmt(a.getHarga())).append("\n");
        return sb.append("\n").toString();
    }

    private String lanjutPrompt(ChatSession session) {
        if (!session.draftItems.isEmpty())
            return "Draft sebelumnya masih ada. Ketik selesai untuk konfirmasi, atau tambah layanan baru.";
        return "Ada yang bisa saya bantu?";
    }

    private String ringkasanDraftSementara(ChatSession session) {
        if (session.draftItems.isEmpty()) return "";
        StringBuilder sb = new StringBuilder("Item sejauh ini:\n");
        int no = 1;
        double grandTotal = 0;
        for (ItemDraft d : session.draftItems) {
            sb.append(no++).append(". ").append(d.layanan.getNamaLayanan());
            if (d.layanan.isPerKg() && d.beratKg > 0)
                sb.append(" x ").append(d.beratKg).append(" kg");
            else if (!d.layanan.isPerKg() && d.jumlahItem > 0)
                sb.append(" x ").append(d.jumlahItem).append(" item");
            if ("EXPRESS".equals(d.kecepatan)) sb.append(" [Express]");
            if (!d.addonNama.isBlank()) sb.append(" + ").append(d.addonNama);
            sb.append("  → Rp").append(fmt(d.totalItem)).append("\n");
            grandTotal += d.totalItem;
        }
        sb.append("Subtotal sementara: Rp").append(fmt(grandTotal)).append("\n");
        return sb.toString();
    }

    private BotResponse tampilNota(ChatSession session) {
        if (session.draftItems.isEmpty())
            return txt("Tidak ada item dalam pesanan. Mulai dengan memilih layanan.");

        StringBuilder sb = new StringBuilder();
        sb.append("RINGKASAN PESANAN\n");
        sb.append("========================\n");

        double grandTotal = 0;
        int no = 1;
        for (ItemDraft d : session.draftItems) {
            sb.append("ITEM ").append(no++).append(" — ").append(d.layanan.getNamaLayanan()).append("\n");

            if (d.layanan.isPerKg() && d.beratKg > 0)
                sb.append("  Rp").append(fmt(d.layanan.getHarga())).append("/kg x ")
                        .append(d.beratKg).append(" kg = Rp").append(fmt(d.subtotalLayanan)).append("\n");
            else if (!d.layanan.isPerKg() && d.jumlahItem > 0)
                sb.append("  Rp").append(fmt(d.layanan.getHarga())).append("/item x ")
                        .append(d.jumlahItem).append(" item = Rp").append(fmt(d.subtotalLayanan)).append("\n");

            if (d.expressTotal > 0)
                sb.append("  Express: +Rp").append(fmt(d.expressTotal)).append("\n");

            if (!d.addonNama.isBlank())
                for (String a : d.addonNama.split(","))
                    sb.append("  Add-on ").append(a.trim()).append(": +Rp").append("(lihat rincian)\n");

            if (d.addonTotal > 0)
                sb.append("  Total add-on: Rp").append(fmt(d.addonTotal)).append("\n");

            sb.append("  Subtotal item: Rp").append(fmt(d.totalItem)).append("\n");
            sb.append("  Kecepatan: ").append(d.kecepatan).append("\n\n");
            grandTotal += d.totalItem;
        }

        sb.append("========================\n");
        sb.append("TOTAL KESELURUHAN: Rp").append(fmt(grandTotal)).append("\n\n");
        sb.append("Apakah pesanan sudah benar?\n");
        sb.append("- Ketik ya    → konfirmasi & simpan\n");
        sb.append("- Ketik batal → batalkan semua\n");
        sb.append("- Ketik ubah  → ulangi dari awal");
        return txt(sb.toString());
    }

    private BotResponse simpanPesanan(ChatSession session) {
        if (session.draftItems.isEmpty()) return txt("Tidak ada item untuk disimpan.");

        double grandTotal = session.draftItems.stream().mapToDouble(d -> d.totalItem).sum();
        String kode       = pesananService.simpanPesananDariChat(session.draftItems);
        String estimasi;
        if (session.draftItems.size() == 1) {
            ItemDraft d = session.draftItems.get(0);
            estimasi = "EXPRESS".equals(d.kecepatan) ? "1 Hari Kerja" : d.layanan.getEstimasiWaktu();
        } else {
            estimasi = "Sesuai layanan masing-masing item";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Pesanan berhasil disimpan!\n\n");
        sb.append("========================\n");
        sb.append("ID Pesanan   : ").append(kode).append("\n");
        sb.append("Tanggal      : ").append(LocalDate.now()).append("\n");
        sb.append("Jumlah Item  : ").append(session.draftItems.size()).append(" layanan\n");
        sb.append("========================\n\n");

        int no = 1;
        for (ItemDraft d : session.draftItems) {
            sb.append("Item ").append(no++).append(": ").append(d.layanan.getNamaLayanan());
            if (d.layanan.isPerKg() && d.beratKg > 0)
                sb.append(" x ").append(d.beratKg).append(" kg");
            else if (!d.layanan.isPerKg() && d.jumlahItem > 0)
                sb.append(" x ").append(d.jumlahItem).append(" item");
            if ("EXPRESS".equals(d.kecepatan)) sb.append(" [Express]");
            if (!d.addonNama.isBlank()) sb.append("\n       Add-on: ").append(d.addonNama);
            sb.append("  → Rp").append(fmt(d.totalItem)).append("\n");
        }

        sb.append("========================\n");
        sb.append("TOTAL        : Rp").append(fmt(grandTotal)).append("\n\n");
        sb.append("Status       : DITERIMA\n");
        sb.append("Est. Selesai : ").append(estimasi).append("\n\n");
        sb.append("Alur: Diterima > Diproses > Selesai > Sudah Diambil\n\n");
        sb.append("Simpan kode ").append(kode).append(" untuk cek status.\n");
        sb.append("Terima kasih sudah menggunakan Washie!");

        resetSemua(session);
        return new BotResponse(sb.toString(), ResponseType.NOTA, kode);
    }

    private double hitungSubtotal(ChatSession s) {
        return s.layanan.isPerKg()
                ? s.layanan.getHarga() * s.beratKg
                : s.layanan.getHarga() * s.jumlahItem;
    }

    private double getExpressRate(Layanan l) {
        if (l.isPerKg())
            return infoService.getNilai("ADDON_CONFIG","express_surcharge_per_kg")
                    .map(Double::parseDouble).orElse(5000d);
        return infoService.getNilai("ADDON_CONFIG","express_surcharge_flat")
                .map(Double::parseDouble).orElse(15000d);
    }

    private double hitungExpress(ChatSession s) {
        double rate = getExpressRate(s.layanan);
        if (s.layanan.isPerKg()) {
            double kg = s.beratKg > 0 ? s.beratKg : 1;
            return rate * kg;
        }
        return rate;
    }

    private boolean addonDipilih(String namaAddon, String userInput) {
        if (userInput.contains(namaAddon)) return true;
        if (namaAddon.equals("extra softener"))
            return (has(userInput,"extra softener") || has(userInput,"softener","pelembut")) &&
                    !has(userInput,"anti","antiseptik","septik","kuman");
        if (namaAddon.equals("anti-septik") || namaAddon.equals("anti septik"))
            return has(userInput,"anti-septik","anti septik","antiseptik","kuman","antibakteri") &&
                    !has(userInput,"softener","pelembut");
        if (namaAddon.contains("softener") && namaAddon.contains("anti"))
            return (has(userInput,"softener") && has(userInput,"anti","antiseptik","septik")) ||
                    has(userInput,"softener anti","softener+anti");
        String[] words = namaAddon.split("[\\s\\-+/]+");
        long required = Arrays.stream(words).filter(w -> w.length() >= 4).count();
        if (required == 0) return false;
        long matched  = Arrays.stream(words).filter(w -> w.length() >= 4 && userInput.contains(w)).count();
        return matched == required;
    }

    private BotResponse handleInfo(String tipe, String namaLayanan) {
        Optional<Layanan> opt = cariLayananDb(namaLayanan);
        if (opt.isEmpty()) return txt("Maaf, informasi layanan " + namaLayanan + " belum tersedia.");
        Layanan l     = opt.get();
        String satuan = l.isPerKg() ? "/kg" : "/item";
        double expRate = getExpressRate(l);

        return switch (tipe) {
            case "HARGA" -> {
                StringBuilder sb = new StringBuilder();
                sb.append("Harga layanan ").append(l.getNamaLayanan()).append(":\n\n");
                sb.append("- Standar : Rp").append(fmt(l.getHarga())).append(satuan)
                        .append("  (estimasi ").append(l.getEstimasiWaktu()).append(")\n");
                if (l.isBisaExpress())
                    sb.append("- Express : +Rp").append(fmt(expRate)).append(satuan)
                            .append("  (1 Hari Kerja)\n");
                else sb.append("- Express : tidak tersedia\n");
                sb.append("\nContoh: 3 ").append(l.isPerKg() ? "kg" : "item")
                        .append(" x Rp").append(fmt(l.getHarga())).append(" = Rp").append(fmt(l.getHarga() * 3)).append("\n\n");
                sb.append("Mau pesan? Ketik: ").append(l.getNamaLayanan().toLowerCase())
                        .append(l.isPerKg() ? " 2 kg" : " 1 item");
                yield txt(sb.toString());
            }
            case "ESTIMASI" -> txt(
                    "Estimasi waktu " + l.getNamaLayanan() + ":\n\n" +
                            "- Standar : " + l.getEstimasiWaktu() + "\n" +
                            "- Express : " + (l.isBisaExpress()
                            ? "1 Hari Kerja (+Rp" + fmt(expRate) + satuan + ")"
                            : "tidak tersedia") + "\n\n" +
                            "Estimasi dihitung sejak pakaian diterima.\n" +
                            "Harga: Rp" + fmt(l.getHarga()) + satuan + "\n\n" +
                            "Mau pesan? Ketik: " + l.getNamaLayanan().toLowerCase() +
                            (l.isPerKg() ? " 2 kg" : " 1 item")
            );
            case "DESKRIPSI" -> {
                Map<String,String> desk = new HashMap<>();
                desk.put("Dry Cleaning","Pencucian dengan cairan kimia khusus (bukan air). Cocok untuk jas, kebaya, gaun pengantin, wol, sutra.");
                desk.put("Cuci Kering","Cuci + pengeringan mesin tanpa setrika. Untuk pakaian kasual sehari-hari.");
                desk.put("Cuci + Setrika","Cuci, keringkan, lalu setrika rapi. Untuk kemeja dan pakaian formal.");
                desk.put("Setrika Saja","Penyetrikaan untuk pakaian yang sudah bersih.");
                desk.put("Cuci Bedcover","Pencucian bedcover dengan mesin besar. Harga per item.");
                desk.put("Cuci Boneka","Pencucian lembut stuffed toy. Harga per item.");
                desk.put("Cuci Karpet","Pencucian karpet dengan mesin khusus. Harga per m².");
                desk.put("Cuci Selimut","Pencucian selimut tebal/tipis. Harga per item.");
                desk.put("Cuci Handuk","Pencucian handuk menjaga kelembutan bahan.");
                desk.put("Cuci Gorden & Vitrase","Pencucian tirai/gorden/vitrase hati-hati. Harga per item.");
                desk.put("Cuci Sprei & Sarung Bantal","Pencucian sprei + sarung bantal. Harga per set.");
                yield txt(l.getNamaLayanan() + "\n\n" +
                        desk.getOrDefault(l.getNamaLayanan(),"Layanan pencucian profesional.") +
                        "\n\nHarga    : Rp" + fmt(l.getHarga()) + satuan +
                        "\nEstimasi : " + l.getEstimasiWaktu() +
                        "\nExpress  : " + (l.isBisaExpress() ? "tersedia (+Rp" + fmt(expRate) + satuan + ")" : "tidak tersedia") +
                        "\n\nMau pesan? Ketik: " + l.getNamaLayanan().toLowerCase() +
                        (l.isPerKg() ? " 2 kg" : " 1 item"));
            }
            default -> respFallback();
        };
    }

    private BotResponse respTanyaLayanan() {
        List<Layanan> aktif = layananService.getLayananAktif();
        if (aktif.isEmpty()) return txt("Maaf, belum ada layanan aktif. Hubungi kami via WhatsApp.");
        StringBuilder sb = new StringBuilder("Mau laundry apa?\n\n");
        List<Layanan> perKg   = aktif.stream().filter(Layanan::isPerKg).collect(Collectors.toList());
        List<Layanan> perItem = aktif.stream().filter(l -> !l.isPerKg()).collect(Collectors.toList());
        if (!perKg.isEmpty()) {
            sb.append("Per Kilogram:\n");
            for (Layanan l : perKg)
                sb.append(String.format("- %-28s Rp%s/kg\n", l.getNamaLayanan(), fmt(l.getHarga())));
        }
        if (!perItem.isEmpty()) {
            sb.append("\nPer Item:\n");
            for (Layanan l : perItem)
                sb.append(String.format("- %-28s Rp%s/item\n", l.getNamaLayanan(), fmt(l.getHarga())));
        }
        sb.append("\nKetik nama layanan, misal:\n  cuci setrika 2 kg\n  cuci bedcover 2 item");
        return txt(sb.toString());
    }

    private BotResponse respDaftarLayanan() { return respTanyaLayanan(); }

    private BotResponse cekStatus(String kode) {
        return pesananService.getByKode(kode).map(p -> {
            String st = switch (p.getStatus()) {
                case DIPROSES -> "Sedang Diproses";
                case SELESAI  -> "Selesai - siap diambil!";
                case DIAMBIL  -> "Sudah Diambil";
            };
            String items = p.getItems().size() > 1
                    ? "\nJumlah Item: " + p.getItems().size() + " layanan" : "";
            return txt("Status Pesanan " + p.getKodePesanan() + ":\n\n" +
                    "Nama    : " + p.getUser().getNamaLengkap() + "\n" +
                    "Layanan : " + p.getLayanan().getNamaLayanan() +
                    (p.getItems().size() > 1 ? " + " + (p.getItems().size()-1) + " lainnya" : "") + "\n" +
                    "Masuk   : " + p.getTanggalMasuk() + items + "\n" +
                    "Total   : Rp" + (p.getTotalHarga() != null ? fmt(p.getTotalHarga()) : "-") + "\n" +
                    "Status  : " + st);
        }).orElse(txt("Pesanan " + kode + " tidak ditemukan.\nPastikan kode benar, contoh: WS-001."));
    }

    private BotResponse respJam() {
        String sf = infoService.getNilai("JAM_OPERASIONAL","senin_jumat").orElse("08.00-21.00");
        String sm = infoService.getNilai("JAM_OPERASIONAL","sabtu_minggu").orElse("09.00-19.00");
        String hl = infoService.getNilai("JAM_OPERASIONAL","hari_libur").orElse("Tutup");
        return txt("Jam Operasional Washie:\n\nSenin-Jumat  : " + sf +
                "\nSabtu-Minggu : " + sm + "\nHari Libur   : " + hl);
    }

    private BotResponse respLokasi() {
        String al = infoService.getNilai("LOKASI_KONTAK","alamat").orElse("-");
        String wa = infoService.getNilai("LOKASI_KONTAK","whatsapp").orElse("-");
        String ig = infoService.getNilai("LOKASI_KONTAK","instagram").orElse("-");
        return txt("Lokasi & Kontak Washie:\n\nAlamat    : " + al +
                "\nWhatsApp  : " + wa + "\nInstagram : " + ig);
    }

    private BotResponse respPengumuman() {
        List<InfoEntity> list = infoService.getPengumuman();
        if (list.isEmpty())
            return txt("Saat ini belum ada pengumuman dari Washie.\nHubungi WhatsApp kami untuk info lebih lanjut.");
        StringBuilder sb = new StringBuilder("Informasi terbaru dari Washie:\n\n");
        for (InfoEntity p : list) {
            sb.append("[").append(p.getKunci()).append("]\n").append(p.getNilai()).append("\n\n");
        }
        return txt(sb.toString().trim());
    }

    private BotResponse salam() {
        StringBuilder sb = new StringBuilder("Halo! Selamat datang di Washie Laundry.\n\n");
        List<InfoEntity> pengumuman = infoService.getPengumuman();
        if (!pengumuman.isEmpty()) {
            sb.append("--- Info dari Washie ---\n");
            for (InfoEntity p : pengumuman)
                sb.append("[").append(p.getKunci()).append("]\n").append(p.getNilai()).append("\n\n");
            sb.append("------------------------\n\n");
        }
        sb.append("Saya bisa membantu:\n");
        sb.append("- Pesan layanan  : cuci setrika 2 kg  /  cuci bedcover 1 item\n");
        sb.append("- Pesan multi    : bisa tambah beberapa layanan dalam 1 nota\n");
        sb.append("- Cek harga      : berapa harga cuci setrika\n");
        sb.append("- Estimasi       : berapa lama cuci kering\n");
        sb.append("- Info layanan   : apa itu dry cleaning\n");
        sb.append("- Status pesanan : cek WS-001\n");
        sb.append("- Jam buka       : jam operasional\n");
        sb.append("- Lokasi         : lokasi\n\n");
        sb.append("Ada yang bisa saya bantu?");
        return txt(sb.toString());
    }

    private BotResponse respFallback() {
        return txt("Maaf, saya belum mengerti.\n\n" +
                "Coba ketik:\n" +
                "- cuci setrika 2 kg       : pesan per kg\n" +
                "- cuci bedcover 2 item    : pesan per item\n" +
                "- berapa harga cuci kering: cek harga\n" +
                "- berapa lama cuci setrika: estimasi waktu\n" +
                "- daftar layanan          : semua layanan\n" +
                "- cek WS-001              : status pesanan");
    }

    private Optional<Layanan> cariLayananDb(String term) {
        List<Layanan> aktif = layananService.getLayananAktif();
        String kw = term.toLowerCase();
        for (Layanan l : aktif) if (l.getNamaLayanan().equalsIgnoreCase(term)) return Optional.of(l);
        for (Layanan l : aktif) if (l.getNamaLayanan().toLowerCase().contains(kw)) return Optional.of(l);
        for (String w : kw.split("\\s+")) {
            if (w.length() < 3) continue;
            for (Layanan l : aktif) if (l.getNamaLayanan().toLowerCase().contains(w)) return Optional.of(l);
        }
        return Optional.empty();
    }

    private double extractAngka(String input) {
        Matcher m = ANGKA_PAT.matcher(input);
        while (m.find()) {
            try {
                double v = Double.parseDouble(m.group(1).replace(",","."));
                if (v > 0 && v < 10000) return v;
            } catch (NumberFormatException ignored) {}
        }
        return 0;
    }

    private String cariKode(String input) {
        Matcher m = Pattern.compile("\\b(WS-\\d+)\\b", Pattern.CASE_INSENSITIVE).matcher(input);
        return m.find() ? m.group(1).toUpperCase() : null;
    }

    private boolean has(String input, String... kws) {
        for (String k : kws) if (input.contains(k)) return true;
        return false;
    }

    private boolean isBatal(String lower) {
        return has(lower,"batal","cancel","tidak jadi","ga jadi","gak jadi");
    }

    private String fmt(double v) {
        return v == Math.floor(v) ? String.format("%.0f", v) : String.format("%.1f", v);
    }

    private void resetItem(ChatSession s) {
        s.layanan      = null;
        s.beratKg      = 0;
        s.jumlahItem   = 0;
        s.kecepatan    = null;
        s.expressTotal = 0;
        s.addonNama.clear();
        s.addonHarga.clear();
    }

    private void resetSemua(ChatSession s) {
        resetItem(s);
        s.draftItems.clear();
        s.state = ConvState.IDLE;
    }

    private static BotResponse txt(String msg) { return new BotResponse(msg, ResponseType.TEXT, null); }

    public enum ResponseType { TEXT, LAYANAN, NOTA }

    public static class BotResponse {
        private final String text; private final ResponseType type; private final Object data;
        public BotResponse(String t, ResponseType r, Object d) { text=t; type=r; data=d; }
        public String getText() { return text; }
        public ResponseType getType() { return type; }
        public Object getData() { return data; }
    }

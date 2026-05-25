package com.washie.engine;

import com.washie.model.InfoEntity;
import com.washie.model.Layanan;
import com.washie.model.Pesanan;
import com.washie.service.InfoService;
import com.washie.service.LayananService;
import com.washie.service.PesananService;
import com.washie.service.PesananService.ItemDraft;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.*;
import java.util.regex.*;
import java.util.stream.Collectors;

/**
 * ChatEngine — NLP Multi-Order Parser dengan Global Speed/Addon Mode.
 *
 * Mode yang didukung:
 *
 * MODE A — Per-item lengkap dalam 1 bubble:
 *   "cuci setrika 3kg express pewangi premium, cuci boneka 2 item standar"
 *
 * MODE B — Daftar dulu, kecepatan+addon belakangan:
 *   Bubble 1: "cuci setrika 3kg, cuci kering 2kg, bedcover 1 item"
 *   Bot: "Mau kecepatan apa untuk semua? Dan add-on apa?"
 *   Bubble 2: "semua express, pewangi premium"
 *
 * MODE C — Kecepatan/addon berbeda per layanan, bilang di akhir:
 *   Bubble 1: "cuci setrika 3kg, cuci kering 2kg, bedcover 1 item"
 *   Bubble 2: "setrika express pewangi, kering standar tanpa addon, bedcover standar semua addon"
 */
@Component
public class ChatEngine {

    private final LayananService layananService;
    private final PesananService pesananService;
    private final InfoService    infoService;

    public ChatEngine(LayananService l, PesananService p, InfoService i) {
        this.layananService = l; this.pesananService = p; this.infoService = i;
    }

    // =========================================================================
    //  VALIDASI
    // =========================================================================
    private static final double MIN_BERAT_KG = 1.0;
    private static final double MAX_BERAT_KG = 25.0;
    private static final int    MIN_ITEM      = 1;
    private static final int    MAX_ITEM      = 20;

    // =========================================================================
    //  SESSION
    // =========================================================================
    public static class ChatSession {
        public ConvState state = ConvState.IDLE;

        // Mode interaktif 1 item
        public Layanan      layanan;
        public double       beratKg      = 0;
        public int          jumlahItem   = 0;
        public String       kecepatan;
        public double       expressTotal = 0;
        public List<String> addonNama    = new ArrayList<>();
        public List<Double> addonHarga   = new ArrayList<>();

        // Draft items siap simpan
        public List<ItemDraft> draftItems = new ArrayList<>();

        // Mode B/C: item sudah di-parse kuantitas, menunggu kecepatan+addon
        public List<ParsedOrder> pendingSpeedAddon = new ArrayList<>();

        // Clarification untuk item yang kurang info kuantitas
        public List<ParsedOrder> pendingClarification = new ArrayList<>();
        public int clarificationIndex = 0;

        public Set<String> downgradedLayanan = new HashSet<>();

        // Untuk melacak item nomor berapa yang sedang diedit
        public int editIndex = -1;
    }

    public enum ConvState {
        IDLE,
        TANYA_BERAT,
        TANYA_JUMLAH,
        PILIH_KECEPATAN,
        PILIH_ADDON,
        TANYA_TAMBAH_ITEM,
        KONFIRMASI,
        CLARIFICATION,
        TANYA_SPEED_ADDON_GLOBAL, // Mode B: tanya kecepatan+addon untuk semua item
        TANYA_DOWNGRADE,
        PILIH_ITEM_UBAH,
        PILIH_ASPEK_UBAH,
        UBAH_KUANTITAS,
        UBAH_KECEPATAN,
        UBAH_ADDON
    }

    // =========================================================================
    //  PARSED ORDER
    // =========================================================================
    public static class ParsedOrder {
        public String  rawSegment;
        public Layanan layanan;
        public double  beratKg    = 0;
        public int     jumlahItem = 0;
        public String  kecepatan;          // null = belum ditentukan
        public List<String> addonName  = new ArrayList<>();
        public List<Double> addonHarga = new ArrayList<>();
        public boolean tanpaAddon = false;
        public boolean semuaAddon = false;
        public String  errorMsg;
        public boolean isDowngraded = false;

        public boolean kuantitasLengkap() {
            if (layanan == null) return false;
            return layanan.isPerKg() ? beratKg > 0 : jumlahItem > 0;
        }

        public boolean isComplete() {
            return kuantitasLengkap() && kecepatan != null;
        }
    }

    // =========================================================================
    //  KEYWORD PATTERNS
    // =========================================================================
    private static final List<String[]> LAYANAN_PAT = new ArrayList<>();
    static {
        LAYANAN_PAT.add(new String[]{"cuci\\s*(\\+\\s*)?setrika|wash.*iron",             "Cuci + Setrika"});
        LAYANAN_PAT.add(new String[]{"cuci\\s*kering(?!\\s*setrika)|wash\\s*only",       "Cuci Kering"});
        LAYANAN_PAT.add(new String[]{"setrika\\s*(saja|aja|only|doank)|ironing\\s*only", "Setrika Saja"});
        LAYANAN_PAT.add(new String[]{"dry\\s*clean(?:ing)?",                             "Dry Cleaning"});
        LAYANAN_PAT.add(new String[]{"bedcover|bed\\s*cover",                            "Cuci Bedcover"});
        LAYANAN_PAT.add(new String[]{"sprei|sarung\\s*bantal",                           "Cuci Sprei"});
        LAYANAN_PAT.add(new String[]{"selimut|blanket",                                  "Cuci Selimut"});
        LAYANAN_PAT.add(new String[]{"handuk|towel",                                     "Cuci Handuk"});
        LAYANAN_PAT.add(new String[]{"gorden|gordyn|vitrase|tirai",                      "Cuci Gorden"});
        LAYANAN_PAT.add(new String[]{"karpet|carpet",                                    "Cuci Karpet"});
        LAYANAN_PAT.add(new String[]{"boneka|stuffed|teddy",                             "Cuci Boneka"});
    }

    private static final List<String[]> INFO_MAP = new ArrayList<>();
    static {
        String[] names = {"Cuci + Setrika","Cuci Kering","Setrika Saja","Dry Cleaning","Cuci Bedcover","Cuci Sprei","Cuci Selimut","Cuci Handuk","Cuci Gorden","Cuci Karpet","Cuci Boneka"};
        String[] regex = {"cuci.*setrika","cuci\\s*kering","setrika\\s*saja","dry\\s*clean","bedcover","sprei","selimut","handuk","gorden","karpet","boneka"};
        for (int i = 0; i < names.length; i++) {
            INFO_MAP.add(new String[]{"(harga|biaya|tarif|berapa).*(" + regex[i] + ")",           "HARGA",    names[i]});
            INFO_MAP.add(new String[]{"(lama|estimasi|kapan|selesai|waktu).*(" + regex[i] + ")",  "ESTIMASI", names[i]});
            INFO_MAP.add(new String[]{"(apa|jelaskan|info|maksud|ceritakan).*(" + regex[i] + ")", "DESKRIPSI",names[i]});
        }
    }

    private static final String[] LOKASI_KW     = {"di mana","dimana","lokasi","alamat","ada di mana","tempatnya","laundry di mana","laundry ada di","di mana laundry","washie di mana","di mana washie","kontak","whatsapp","wa ","instagram","ig ","hubungi","nomor","no hp","nomer hp"};
    private static final String[] JAM_KW        = {"jam buka","jam tutup","jam operasional","buka jam","tutup jam","jam berapa","operasional","waktu buka","hari apa","buka hari","kapan buka","hari senin","hari minggu","hari libur","buka sampai"};
    private static final String[] DAFTAR_KW     = {"daftar layanan","list layanan","layanan apa","layanan ada apa","ada layanan apa","apa saja layanan","layanan tersedia","pilihan layanan","menu layanan","semua layanan","layanan yang ada","ada apa aja","apa aja layanan"};
    private static final String[] PENGUMUMAN_KW = {"pengumuman","info terbaru","ada info","ada promo","promo","info hari ini","berita","update","announcement","informasi terbaru"};

    // Keyword kecepatan
    private static final Pattern PAT_EXPRESS = Pattern.compile("\\b(express|ekspres|kilat|cepat|1\\s*hari|sehari)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern PAT_STANDAR  = Pattern.compile("\\b(standar|standard|biasa|normal|reguler|2\\s*hari|3\\s*hari)\\b", Pattern.CASE_INSENSITIVE);

    // Keyword addon global
    private static final Pattern PAT_TANPA_ADDON = Pattern.compile("\\b(tanpa\\s+(?:add[- ]?on|tambahan|pewangi|softener|anti)|tidak\\s+pakai|no\\s+addon|without)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern PAT_SEMUA_ADDON = Pattern.compile("\\b(semua\\s+(?:add[- ]?on|addon|tambahan)|all\\s+addon|keduanya|semua\\s+produk)\\b", Pattern.CASE_INSENSITIVE);

    // Angka
    private static final Pattern PAT_KG   = Pattern.compile("(\\d+(?:[.,]\\d+)?)\\s*(?:kg|kilo(?:gram)?)", Pattern.CASE_INSENSITIVE);
    private static final Pattern PAT_ITEM = Pattern.compile("(\\d+(?:[.,]\\d+)?)\\s*(?:item|pcs|buah|lembar|meter|helai|unit)", Pattern.CASE_INSENSITIVE);
    private static final Pattern PAT_NEG  = Pattern.compile("-\\s*\\d");

    // =========================================================================
    //  MAIN ENTRY
    // =========================================================================
    public BotResponse process(String input, ChatSession session) {
        if (input == null || input.isBlank())
            return txt("Maaf, saya tidak mengerti. Coba ketik ulang ya.");
        String raw   = input.trim();
        String lower = raw.toLowerCase();

        return switch (session.state) {
            case IDLE                 -> handleIdle(raw, lower, session);
            case TANYA_BERAT          -> handleTanyaBerat(raw, lower, session);
            case TANYA_JUMLAH         -> handleTanyaJumlah(raw, lower, session);
            case PILIH_KECEPATAN      -> handlePilihKecepatan(lower, session);
            case PILIH_ADDON          -> handlePilihAddon(lower, session);
            case TANYA_TAMBAH_ITEM    -> handleTanyaTambahItem(raw, lower, session);
            case KONFIRMASI           -> handleKonfirmasi(lower, session);
            case CLARIFICATION        -> handleClarification(raw, lower, session);
            case TANYA_SPEED_ADDON_GLOBAL -> handleSpeedAddonGlobal(raw, lower, session);
            case TANYA_DOWNGRADE      -> handleTanyaDowngrade(raw, lower, session);
            case PILIH_ITEM_UBAH      -> handlePilihItemUbah(raw, lower, session);
            case PILIH_ASPEK_UBAH     -> handlePilihAspekUbah(raw, lower, session);
            case UBAH_KUANTITAS       -> handleUbahKuantitas(raw, lower, session);
            case UBAH_KECEPATAN       -> handleUbahKecepatan(raw, lower, session);
            case UBAH_ADDON           -> handleUbahAddon(raw, lower, session);
        };
    }

    // =========================================================================
    //  STATE: IDLE
    // =========================================================================
    private BotResponse handleIdle(String raw, String lower, ChatSession session) {
        String kode = cariKode(raw);
        if (has(lower, "batal", "batalkan", "cancel") && (has(lower, "pesan", "order", "ws-"))) {
            return handleBatalkanPesanan(raw, lower);
        }
        if (kode != null) return cekStatus(kode);
        if (has(lower,"status pesanan","cek pesanan","lacak")) return txt("Masukkan kode pesanan.\nContoh: cek WS-001");
        if (has(lower, "riwayat", "pesanan saya", "history")) return cekRiwayat();
        if (has(lower,"halo","hai","hello","hi ","selamat pagi","selamat siang","selamat sore","selamat malam","hei","assalamu","permisi"))
            return salam();

        for (String kw : LOKASI_KW)     if (lower.contains(kw)) return respLokasi();
        for (String kw : JAM_KW)        if (lower.contains(kw)) return respJam();
        for (String kw : DAFTAR_KW)     if (lower.contains(kw)) return respDaftarLayanan();
        for (String kw : PENGUMUMAN_KW) if (lower.contains(kw)) return respPengumuman();

        for (String[] row : INFO_MAP)
            if (Pattern.compile(row[0], Pattern.CASE_INSENSITIVE).matcher(raw).find())
                return handleInfo(row[1], row[2]);

        boolean adaLayanan = LAYANAN_PAT.stream()
                .anyMatch(row -> Pattern.compile(row[0], Pattern.CASE_INSENSITIVE).matcher(raw).find());

        if (adaLayanan) return parseMultiOrder(raw, lower, session);

        if (has(lower,"cuci baju","cuci pakaian","mau laundry","mau cuci","pengen laundry","ingin laundry","laundry dong","laundry ya"))
            return respTanyaLayanan();

        if (has(lower,"harga","tarif","biaya")) return respDaftarLayanan();
        if (has(lower,"terima kasih","makasih","thanks","thx","tq"))
            return txt("Sama-sama! Senang bisa membantu.");

        return respFallback();
    }

    // =========================================================================
    //  MULTI-ORDER PARSER
    // =========================================================================
    private BotResponse parseMultiOrder(String raw, String lower, ChatSession session) {
        List<Layanan>     addonsDb = layananService.getAddonAktif();
        List<String>      segments = splitSegments(raw);
        List<ParsedOrder> parsed   = new ArrayList<>();
        List<String>      errors   = new ArrayList<>();

        // Variabel penampung instruksi kecepatan & addon global ("Semua express...")
        String globalSpeed = null;
        boolean globalTanpaAddon = false;
        boolean globalSemuaAddon = false;
        List<String> globalAddonName = new ArrayList<>();
        List<Double> globalAddonHarga = new ArrayList<>();

        for (String seg : segments) {
            if (seg.isBlank()) continue;
            ParsedOrder order = parseOneSegment(seg, seg.toLowerCase(), addonsDb);

            if (order.errorMsg != null) { errors.add("\"" + seg.trim() + "\" → " + order.errorMsg); continue; }

            // Jika segmen ini TIDAK mengandung nama layanan, tangkap sebagai instruksi global!
            if (order.layanan == null) {
                String segLow = seg.toLowerCase();

                if (PAT_EXPRESS.matcher(segLow).find())      globalSpeed = "EXPRESS";
                else if (PAT_STANDAR.matcher(segLow).find()) globalSpeed = "STANDAR";

                if (PAT_TANPA_ADDON.matcher(segLow).find()) globalTanpaAddon = true;
                if (PAT_SEMUA_ADDON.matcher(segLow).find()) globalSemuaAddon = true;

                if (!globalTanpaAddon) {
                    if (globalSemuaAddon) {
                        for (Layanan a : addonsDb) {
                            if (!globalAddonName.contains(a.getNamaLayanan())) {
                                globalAddonName.add(a.getNamaLayanan());
                                globalAddonHarga.add(a.getHarga());
                            }
                        }
                    } else {
                        for (Layanan a : addonsDb) {
                            if (addonDipilih(a.getNamaLayanan().toLowerCase(), segLow)) {
                                if (!globalAddonName.contains(a.getNamaLayanan())) {
                                    globalAddonName.add(a.getNamaLayanan());
                                    globalAddonHarga.add(a.getHarga());
                                }
                            }
                        }
                    }
                }
                continue; // Lanjut ke segmen berikutnya, jangan masuk ke keranjang 'parsed'
            }
            parsed.add(order);
        }

        // ==============================================================
        //  Terapkan Instruksi Global ke Semua Layanan yang Ditemukan
        // ==============================================================
        if (globalSpeed != null || !globalAddonName.isEmpty() || globalTanpaAddon || globalSemuaAddon) {
            for (ParsedOrder o : parsed) {

                // Pengecekan kecepatan dengan validasi Express
                if (o.kecepatan == null && globalSpeed != null) {
                    if (globalSpeed.equals("EXPRESS") && !o.layanan.isBisaExpress()) {
                        o.kecepatan = "STANDAR"; // Paksa jadi standar jika tidak mendukung express
                        o.isDowngraded = true;
                    } else {
                        o.kecepatan = globalSpeed;
                    }
                }

                if (globalTanpaAddon) o.tanpaAddon = true;
                if (globalSemuaAddon) o.semuaAddon = true;

                if (!o.tanpaAddon && !globalAddonName.isEmpty()) {
                    for (int i = 0; i < globalAddonName.size(); i++) {
                        if (!o.addonName.contains(globalAddonName.get(i))) {
                            o.addonName.add(globalAddonName.get(i));
                            o.addonHarga.add(globalAddonHarga.get(i));
                        }
                    }
                }
            }
        }

        if (parsed.isEmpty() && !errors.isEmpty())
            return txt("Ada masalah dengan input kamu:\n\n" +
                    errors.stream().map(e -> "- " + e).collect(Collectors.joining("\n")) +
                    "\n\nSilakan perbaiki dan coba lagi.");

        StringBuilder errHeader = new StringBuilder();
        if (!errors.isEmpty()) {
            errHeader.append("Beberapa item tidak dapat diproses:\n");
            errors.forEach(e -> errHeader.append("- ").append(e).append("\n"));
            errHeader.append("\n");
        }

        if (parsed.isEmpty()) return respFallback();

        // ==============================================================
        //  CEK: apakah semua item sudah punya kuantitas?
        // ==============================================================
        List<ParsedOrder> kurangKuantitas = parsed.stream()
                .filter(o -> !o.kuantitasLengkap()).collect(Collectors.toList());

        if (!kurangKuantitas.isEmpty()) {
            // Simpan yang sudah punya kuantitas ke draft sementara
            List<ParsedOrder> punyaKuantitas = parsed.stream()
                    .filter(ParsedOrder::kuantitasLengkap).collect(Collectors.toList());

            // Cek apakah yang sudah punya kuantitas juga belum ada kecepatan
            // → jika semua item tidak ada kecepatan, mungkin user memang mau bilang kecepatan belakangan
            boolean semuaTanpaKecepatan = parsed.stream().allMatch(o -> o.kecepatan == null);

            if (semuaTanpaKecepatan && kurangKuantitas.isEmpty()) {
                // Semua punya kuantitas tapi tanpa kecepatan → Mode B
                return masukModeBGlobal(parsed, errHeader.toString(), session);
            }

            // Ada yang kurang kuantitas → tanya dulu
            parsed.stream().filter(ParsedOrder::kuantitasLengkap)
                    .forEach(o -> session.draftItems.add(toDraft(o)));
            session.pendingClarification = kurangKuantitas;
            session.clarificationIndex   = 0;
            session.state                = ConvState.CLARIFICATION;
            BotResponse cr = startClarification(session);
            if (!errHeader.isEmpty())
                return new BotResponse(errHeader + cr.getText(), cr.getType(), cr.getData());
            return cr;
        }

        // ==============================================================
        //  Semua item punya kuantitas
        //  Cek kecepatan: apakah ADA item yang tidak punya kecepatan?
        // ==============================================================
        boolean adaYangTanpaKecepatan = parsed.stream().anyMatch(o -> o.kecepatan == null);

        if (adaYangTanpaKecepatan) {
            // MODE B/C: user belum tentukan kecepatan → tanya
            return masukModeBGlobal(parsed, errHeader.toString(), session);
        }

        // Semua lengkap → simpan ke draft dan lewat penjaga gerbang
        for (ParsedOrder o : parsed) {
            if (o.isDowngraded) {
                session.downgradedLayanan.add(o.layanan.getNamaLayanan());
            }
            session.draftItems.add(toDraft(o));
        }

        // PANGGIL PENJAGA GERBANG (Tampung dulu responsnya, jangan langsung di-return)
        BotResponse response = routeToNotaOrDowngrade(session);

        // Jika ada pesan error dari segment lain (errHeader), tempelkan di atas respons penjaga gerbang
        if (!errHeader.isEmpty()) {
            return new BotResponse(errHeader + response.getText(), response.getType(), response.getData());
        }

        // Jika tidak ada error, kembalikan respons murni dari penjaga gerbang
        return response;
    }

    /**
     * Mode B: semua item sudah punya kuantitas, tapi belum ada kecepatan & addon.
     * Bot tanya sekali untuk semua item, atau user bisa tentukan per-item.
     */
    private BotResponse masukModeBGlobal(List<ParsedOrder> orders, String errMsg, ChatSession session) {
        session.pendingSpeedAddon = orders;
        session.state             = ConvState.TANYA_SPEED_ADDON_GLOBAL;

        StringBuilder sb = new StringBuilder();
        if (!errMsg.isBlank()) sb.append(errMsg);

        sb.append("Oke! Saya catat pesanan kamu:\n\n");
        int no = 1;
        for (ParsedOrder o : orders) {
            sb.append(no++).append(". ").append(o.layanan.getNamaLayanan());
            if (o.layanan.isPerKg() && o.beratKg > 0)
                sb.append(" — ").append(o.beratKg).append(" kg");
            else if (!o.layanan.isPerKg() && o.jumlahItem > 0)
                sb.append(" — ").append(o.jumlahItem).append(" item");
            sb.append("\n");
        }
        sb.append("\n");

        // Tampilkan opsi kecepatan dan addon
        sb.append("Sekarang tentukan kecepatan dan add-on.\n\n");
        sb.append("Opsi 1 — Sama untuk semua:\n");
        sb.append("  \"semua express, pewangi premium\"\n");
        sb.append("  \"semua standar, tanpa add on\"\n");
        sb.append("  \"semua standar, semua add on\"\n\n");
        sb.append("Opsi 2 — Berbeda per layanan:\n");
        sb.append("  \"[nama layanan] express [addon], [nama layanan] standar tanpa addon\"\n");
        sb.append("  Contoh: \"setrika express pewangi, kering standar tanpa addon, bedcover standar semua addon\"\n\n");

        if (!layananService.getAddonAktif().isEmpty()) {
            sb.append("Add-on tersedia:\n").append(buatOpsiAddon()).append("\n");
        }
        sb.append("Ketik pilihan kamu:");
        return txt(sb.toString());
    }

    // =========================================================================
    //  STATE: TANYA_SPEED_ADDON_GLOBAL
    // =========================================================================
    private BotResponse handleSpeedAddonGlobal(String raw, String lower, ChatSession session) {
        if (isBatal(lower)) { resetSemua(session); return txt("Pesanan dibatalkan. Ada yang bisa saya bantu?"); }

        List<Layanan>     addonsDb = layananService.getAddonAktif();
        List<ParsedOrder> orders   = session.pendingSpeedAddon;

        // ── Cek apakah ada keyword kecepatan per-layanan (Mode C) ──────────
        boolean adaPerLayanan = orders.stream().anyMatch(o ->
                lower.contains(o.layanan.getNamaLayanan().toLowerCase().split("\\s+")[0]) ||
                        lower.contains(o.layanan.getNamaLayanan().toLowerCase())
        );

        if (adaPerLayanan) {
            // Mode C: parse per-layanan dari kalimat ini
            return parseSpeedAddonPerLayanan(raw, lower, orders, addonsDb, session);
        }

        // ── Mode B: satu kecepatan+addon untuk semua ──────────────────────
        String kecepatan = null;
        if (PAT_EXPRESS.matcher(lower).find()) kecepatan = "EXPRESS";
        else if (PAT_STANDAR.matcher(lower).find()) kecepatan = "STANDAR";

        if (kecepatan == null) {
            return txt("Saya belum mengerti kecepatannya.\n\n" +
                    "Ketik standar atau express (berlaku untuk semua item),\n" +
                    "atau tentukan per-layanan:\n" +
                    "  \"setrika express, kering standar\"");
        }

        // Cek addon global
        boolean tanpa = PAT_TANPA_ADDON.matcher(lower).find();
        boolean semua = PAT_SEMUA_ADDON.matcher(lower).find();

        List<String> addonNama  = new ArrayList<>();
        List<Double> addonHarga = new ArrayList<>();

        if (!tanpa) {
            if (semua) {
                addonsDb.forEach(a -> { addonNama.add(a.getNamaLayanan()); addonHarga.add(a.getHarga()); });
            } else {
                for (Layanan a : addonsDb) {
                    if (addonDipilih(a.getNamaLayanan().toLowerCase(), lower)) {
                        addonNama.add(a.getNamaLayanan());
                        addonHarga.add(a.getHarga());
                    }
                }
            }
        }

        // Terapkan ke semua item
        for (ParsedOrder o : orders) {
            if ("EXPRESS".equals(kecepatan) && !o.layanan.isBisaExpress()) {
                o.kecepatan = "STANDAR";
                o.isDowngraded = true; // Tandai!
            } else {
                o.kecepatan = kecepatan;
            }

            o.tanpaAddon = tanpa;
            o.semuaAddon = semua;
            o.addonName.clear(); o.addonHarga.clear();
            o.addonName.addAll(addonNama); o.addonHarga.addAll(addonHarga);

            // CEK DOWNGRADE SEBELUM MASUK DRAFT
            if (o.isDowngraded) {
                session.downgradedLayanan.add(o.layanan.getNamaLayanan());
            }
            session.draftItems.add(toDraft(o));
        }

        session.pendingSpeedAddon.clear();

        // PANGGIL PENJAGA GERBANG
        return routeToNotaOrDowngrade(session);
    }

    /** Mode C: parse kecepatan+addon berbeda per-layanan */
    private BotResponse parseSpeedAddonPerLayanan(String raw, String lower,
                                                  List<ParsedOrder> orders, List<Layanan> addonsDb, ChatSession session) {

        // Pisah segmen berdasarkan koma
        String[] parts = raw.split(",\\s*|;\\s*");

        // Map nama layanan → order
        Map<String, ParsedOrder> orderMap = new LinkedHashMap<>();
        for (ParsedOrder o : orders)
            orderMap.put(o.layanan.getNamaLayanan().toLowerCase(), o);

        Set<ParsedOrder> sudahDiatur = new HashSet<>();

        for (String part : parts) {
            String partLow = part.toLowerCase().trim();

            // Cari layanan yang disebut di segmen ini
            ParsedOrder target = null;
            for (Map.Entry<String, ParsedOrder> entry : orderMap.entrySet()) {
                // Cek apakah nama layanan (atau kata pertamanya) ada di segmen
                String[] words = entry.getKey().split("\\s+");
                for (String w : words) {
                    if (w.length() >= 4 && partLow.contains(w)) {
                        target = entry.getValue();
                        break;
                    }
                }
                if (target != null) break;
            }

            if (target == null) {
                // Coba match dengan keyword layanan
                for (String[] row : LAYANAN_PAT) {
                    if (Pattern.compile(row[0], Pattern.CASE_INSENSITIVE).matcher(part).find()) {
                        Optional<Layanan> opt = cariLayananDb(row[1]);
                        if (opt.isPresent()) {
                            String key = opt.get().getNamaLayanan().toLowerCase();
                            target = orderMap.get(key);
                        }
                        break;
                    }
                }
            }

            if (target == null) continue;

            // Deteksi kecepatan
            if (PAT_EXPRESS.matcher(partLow).find())       target.kecepatan = "EXPRESS";
            else if (PAT_STANDAR.matcher(partLow).find())  target.kecepatan = "STANDAR";

            // Deteksi addon
            boolean tanpa = PAT_TANPA_ADDON.matcher(partLow).find();
            boolean semua = PAT_SEMUA_ADDON.matcher(partLow).find();
            target.tanpaAddon = tanpa;
            target.semuaAddon = semua;
            target.addonName.clear(); target.addonHarga.clear();

            if (!tanpa) {
                if (semua) {
                    for (Layanan a : addonsDb) {
                        target.addonName.add(a.getNamaLayanan());
                        target.addonHarga.add(a.getHarga());
                    };
                } else {
                    for (Layanan a : addonsDb) {
                        if (addonDipilih(a.getNamaLayanan().toLowerCase(), partLow)) {
                            target.addonName.add(a.getNamaLayanan());
                            target.addonHarga.add(a.getHarga());
                        }
                    }
                }
            }
            sudahDiatur.add(target);
        }

        // Cek item yang belum diatur
        List<ParsedOrder> belumDiatur = orders.stream()
                .filter(o -> !sudahDiatur.contains(o) || o.kecepatan == null)
                .collect(Collectors.toList());

        if (!belumDiatur.isEmpty()) {
            // Tanya sisanya → default standar tanpa addon? atau tanya user
            StringBuilder sb = new StringBuilder("Beberapa layanan belum ditentukan kecepatannya:\n\n");
            belumDiatur.forEach(o -> sb.append("- ").append(o.layanan.getNamaLayanan()).append("\n"));
            sb.append("\nKetik kecepatan untuk item di atas (contoh: semua standar tanpa addon),\n");
            sb.append("atau ketik lewati untuk pakai standar tanpa add-on.");
            // Simpan yang sudah OK
            orders.stream().filter(sudahDiatur::contains).filter(o -> o.kecepatan != null)
                    .forEach(o -> session.draftItems.add(toDraft(o)));
            session.pendingSpeedAddon = belumDiatur;
            return txt(sb.toString());
        }

        // Semua sudah diatur
        for (ParsedOrder o : orders) {
            // CEK DOWNGRADE SEBELUM MASUK DRAFT
            if (o.isDowngraded) {
                session.downgradedLayanan.add(o.layanan.getNamaLayanan());
            }
            session.draftItems.add(toDraft(o));
        }

        session.pendingSpeedAddon.clear();

        // PANGGIL PENJAGA GERBANG
        return routeToNotaOrDowngrade(session);
    }

    // =========================================================================
    //  PARSE SATU SEGMEN
    // =========================================================================
    private ParsedOrder parseOneSegment(String seg, String segLow, List<Layanan> addonsDb) {
        ParsedOrder order = new ParsedOrder();
        order.rawSegment = seg;

        // 1. Deteksi layanan
        for (String[] row : LAYANAN_PAT) {
            if (Pattern.compile(row[0], Pattern.CASE_INSENSITIVE).matcher(seg).find()) {
                Optional<Layanan> opt = cariLayananDb(row[1]);
                if (opt.isPresent()) { order.layanan = opt.get(); break; }
            }
        }
        if (order.layanan == null) return order;

        // 2. Kuantitas + validasi negatif
        if (order.layanan.isPerKg()) {
            if (PAT_NEG.matcher(segLow).find()) { order.errorMsg = "Berat tidak boleh negatif."; return order; }
            double kg = extractKg(segLow);
            if (kg > 0) {
                String err = validasiBerat(kg);
                if (err != null) { order.errorMsg = err; return order; }
                order.beratKg = kg;
            }
        } else {
            if (PAT_NEG.matcher(segLow).find()) { order.errorMsg = "Jumlah tidak boleh negatif."; return order; }
            int item = extractItem(segLow);
            if (item > 0) {
                String err = validasiItem(item);
                if (err != null) { order.errorMsg = err; return order; }
                order.jumlahItem = item;
            }
        }

        // 3. Kecepatan
        if (PAT_EXPRESS.matcher(segLow).find()) {
            if (order.layanan.isBisaExpress()) {
                order.kecepatan = "EXPRESS";
            } else {
                order.kecepatan = "STANDAR";
                order.isDowngraded = true; // Tandai!
            }
        } else if (PAT_STANDAR.matcher(segLow).find()) {
            order.kecepatan = "STANDAR";
        }

        // 4. Addon (opsional)
        order.tanpaAddon = PAT_TANPA_ADDON.matcher(segLow).find();
        order.semuaAddon = PAT_SEMUA_ADDON.matcher(segLow).find();

        if (!order.tanpaAddon) {
            if (order.semuaAddon) {
                addonsDb.forEach(a -> { order.addonName.add(a.getNamaLayanan()); order.addonHarga.add(a.getHarga()); });
            } else {
                for (Layanan a : addonsDb) {
                    if (addonDipilih(a.getNamaLayanan().toLowerCase(), segLow)) {
                        order.addonName.add(a.getNamaLayanan());
                        order.addonHarga.add(a.getHarga());
                    }
                }
            }
        }

        return order;
    }

    // =========================================================================
    //  SPLIT SEGMEN
    // =========================================================================
    private List<String> splitSegments(String raw) {
        // Memisahkan berdasarkan koma, titik koma, enter, ATAU titik yang diikuti spasi
        String[] parts = raw.split("(?i),\\s*|;\\s*|\\.\\s+|\\n+");
        List<String> result = new ArrayList<>();

        for (String part : parts) {
            // Tambahkan "semua" dan "add" ke lookahead agar kalimat seperti "dan semua express" terpisah
            Matcher m = Pattern.compile(
                    "(?i)\\bdan\\b(?=\\s*(?:cuci|setrika|dry|bedcover|sprei|selimut|handuk|gorden|karpet|boneka|semua|add))"
            ).matcher(part);
            if (m.find()) {
                result.add(part.substring(0, m.start()).trim());
                result.add(part.substring(m.end()).trim());
            } else {
                result.add(part.trim());
            }
        }
        return result.stream().filter(s -> !s.isBlank()).collect(Collectors.toList());
    }

    // =========================================================================
    //  VALIDASI
    // =========================================================================
    private String validasiBerat(double kg) {
        if (kg <= 0)            return "Berat harus lebih dari 0 kg.";
        if (kg < MIN_BERAT_KG)  return String.format("Berat minimal %.0f kg (input: %.1f kg).", MIN_BERAT_KG, kg);
        if (kg > MAX_BERAT_KG)  return String.format("Berat maksimal %.0f kg (input: %.1f kg). Hubungi WA untuk pesanan besar.", MAX_BERAT_KG, kg);
        return null;
    }

    private String validasiItem(int item) {
        if (item <= 0)       return "Jumlah harus lebih dari 0.";
        if (item < MIN_ITEM) return "Jumlah minimal " + MIN_ITEM + " item.";
        if (item > MAX_ITEM) return "Jumlah maksimal " + MAX_ITEM + " item. Hubungi WA untuk pesanan besar.";
        return null;
    }

    // =========================================================================
    //  STATE: TANYA BERAT / JUMLAH (mode interaktif)
    // =========================================================================
    private BotResponse handleTanyaBerat(String raw, String lower, ChatSession session) {
        if (isBatal(lower)) { resetItem(session); return txt("Item dibatalkan."); }
        if (PAT_NEG.matcher(lower).find()) return txt("Berat tidak boleh negatif.\nMasukkan berat yang valid (min " + fmt(MIN_BERAT_KG) + " kg, maks " + fmt(MAX_BERAT_KG) + " kg).");
        double kg = extractKg(lower);
        if (kg <= 0) kg = extractBare(lower);
        if (kg <= 0) return txt("Masukkan berat dalam kg.\nContoh: 2 kg  atau  1.5 kg");
        String err = validasiBerat(kg);
        if (err != null) return txt(err + "\nSilakan masukkan berat yang valid.");
        session.beratKg = kg;
        return lanjutSetelahKuantitas(session);
    }

    private BotResponse handleTanyaJumlah(String raw, String lower, ChatSession session) {
        if (isBatal(lower)) { resetItem(session); return txt("Item dibatalkan."); }
        if (PAT_NEG.matcher(lower).find()) return txt("Jumlah tidak boleh negatif.\nMasukkan jumlah yang valid (min " + MIN_ITEM + ", maks " + MAX_ITEM + ").");
        int item = extractItem(lower);
        if (item <= 0) item = (int) extractBare(lower);
        if (item <= 0) return txt("Masukkan jumlah item.\nContoh: 2 item  atau  3");
        String err = validasiItem(item);
        if (err != null) return txt(err + "\nSilakan masukkan jumlah yang valid.");
        session.jumlahItem = item;
        return lanjutSetelahKuantitas(session);
    }

    private BotResponse lanjutSetelahKuantitas(ChatSession session) {
        if (session.layanan.isBisaExpress()) { session.state = ConvState.PILIH_KECEPATAN; return promptKecepatan(session); }
        session.kecepatan = "STANDAR"; session.expressTotal = 0;
        session.state = ConvState.PILIH_ADDON; return promptAddon();
    }

    // =========================================================================
    //  STATE: PILIH KECEPATAN / ADDON (mode interaktif)
    // =========================================================================
    private BotResponse handlePilihKecepatan(String lower, ChatSession session) {
        if (isBatal(lower)) { resetItem(session); return txt("Item dibatalkan."); }
        if (PAT_EXPRESS.matcher(lower).find()) {
            session.kecepatan = "EXPRESS"; session.expressTotal = hitungExpressSesi(session);
            session.state = ConvState.PILIH_ADDON; return promptAddon();
        }
        if (PAT_STANDAR.matcher(lower).find()) {
            session.kecepatan = "STANDAR"; session.expressTotal = 0;
            session.state = ConvState.PILIH_ADDON; return promptAddon();
        }
        return promptKecepatan(session);
    }

    private BotResponse handlePilihAddon(String lower, ChatSession session) {
        if (isBatal(lower)) { resetItem(session); return txt("Item dibatalkan."); }
        if (has(lower,"tidak","tidak perlu","no","gak","ga","tanpa","skip","langsung","udah","lanjut")) return selesaiItem(session);

        List<Layanan> addons = layananService.getAddonAktif();
        boolean ada = false;
        if (PAT_SEMUA_ADDON.matcher(lower).find()) {
            addons.forEach(a -> { if (!session.addonNama.contains(a.getNamaLayanan())) { session.addonNama.add(a.getNamaLayanan()); session.addonHarga.add(a.getHarga()); ada_set(session); } });
            ada = !addons.isEmpty();
        } else {
            for (Layanan a : addons) {
                if (session.addonNama.contains(a.getNamaLayanan())) continue;
                if (addonDipilih(a.getNamaLayanan().toLowerCase(), lower)) { session.addonNama.add(a.getNamaLayanan()); session.addonHarga.add(a.getHarga()); ada = true; }
            }
        }
        if (ada) return selesaiItem(session);
        return txt("Maaf, tidak mengenali pilihan.\n\n" + buatOpsiAddon() + "Ketik nama add-on, atau tidak jika tidak perlu.");
    }

    private boolean adaFlag = false;
    private void ada_set(ChatSession s) { adaFlag = true; } // dummy untuk lambda

    private BotResponse selesaiItem(ChatSession session) {
        double sub  = hitungSubtotalSesi(session);
        double add  = session.addonHarga.stream().mapToDouble(Double::doubleValue).sum();
        double tot  = sub + session.expressTotal + add;
        session.draftItems.add(new ItemDraft(session.layanan, session.beratKg, session.jumlahItem, session.kecepatan, session.expressTotal, String.join(", ", session.addonNama), add, sub, tot));
        resetItem(session);
        session.state = ConvState.TANYA_TAMBAH_ITEM;
        return txt(ringkasanDraft(session) + "\nMau tambah layanan lagi?\n- Ketik nama layanan → tambah\n- Ketik selesai / tidak → konfirmasi");
    }

    // =========================================================================
    //  STATE: TANYA TAMBAH ITEM
    // =========================================================================
    private BotResponse handleTanyaTambahItem(String raw, String lower, ChatSession session) {
        if (has(lower,"batal semua","cancel semua")) { resetSemua(session); return txt("Semua pesanan dibatalkan."); }
        if (has(lower,"selesai","tidak","no","gak","ga","sudah","cukup")) { session.state = ConvState.KONFIRMASI; return tampilNota(session); }
        boolean adaL = LAYANAN_PAT.stream().anyMatch(row -> Pattern.compile(row[0], Pattern.CASE_INSENSITIVE).matcher(raw).find());
        if (adaL) { session.state = ConvState.IDLE; return parseMultiOrder(raw, lower, session); }
        if (has(lower,"ya","iya","yes","tambah")) { session.state = ConvState.IDLE; return respTanyaLayanan(); }
        return txt("Ketik nama layanan untuk tambah, atau selesai untuk konfirmasi.");
    }

    // =========================================================================
    //  STATE: CLARIFICATION
    // =========================================================================
    private BotResponse startClarification(ChatSession session) {
        return askClarification(session.pendingClarification.get(session.clarificationIndex), session);
    }

    private BotResponse askClarification(ParsedOrder o, ChatSession session) {
        int total = session.pendingClarification.size();
        int cur   = session.clarificationIndex + 1;
        String header = "Info tambahan " + (total > 1 ? "(" + cur + "/" + total + ") " : "") + "untuk: " + o.layanan.getNamaLayanan() + "\n\n";
        if (o.layanan.isPerKg() && o.beratKg <= 0)
            return txt(header + "Berapa berat pakaiannya? (min " + fmt(MIN_BERAT_KG) + " kg, maks " + fmt(MAX_BERAT_KG) + " kg)\nContoh: 2 kg  atau  3.5 kg");
        if (!o.layanan.isPerKg() && o.jumlahItem <= 0)
            return txt(header + "Berapa jumlah item? (min 1, maks " + MAX_ITEM + ")\nContoh: 2 item  atau  3");
        return nextClarification(session);
    }

    private BotResponse handleClarification(String raw, String lower, ChatSession session) {
        if (isBatal(lower)) { resetSemua(session); return txt("Pesanan dibatalkan."); }
        ParsedOrder cur = session.pendingClarification.get(session.clarificationIndex);
        List<Layanan> addonsDb = layananService.getAddonAktif();

        if (cur.layanan.isPerKg() && cur.beratKg <= 0) {
            if (PAT_NEG.matcher(lower).find()) return txt("Berat tidak boleh negatif. Masukkan ulang.");
            double kg = extractKg(lower); if (kg <= 0) kg = extractBare(lower);
            if (kg <= 0) return txt("Masukkan berat dalam kg. Contoh: 2 kg");
            String err = validasiBerat(kg); if (err != null) return txt(err);
            cur.beratKg = kg;
        } else if (!cur.layanan.isPerKg() && cur.jumlahItem <= 0) {
            if (PAT_NEG.matcher(lower).find()) return txt("Jumlah tidak boleh negatif. Masukkan ulang.");
            int item = extractItem(lower); if (item <= 0) item = (int) extractBare(lower);
            if (item <= 0) return txt("Masukkan jumlah item. Contoh: 2");
            String err = validasiItem(item); if (err != null) return txt(err);
            cur.jumlahItem = item;
        }

        // Setelah kuantitas lengkap, simpan ke draft (kecepatan/addon akan ditanya global)
        // Cek apakah semua clarification selesai
        session.clarificationIndex++;
        boolean adaLagi = session.clarificationIndex < session.pendingClarification.size();
        if (adaLagi) return startClarification(session);

        // Semua clarification selesai
        // Gabungkan yang sudah di draft + hasil clarification → masuk mode global
        List<ParsedOrder> all = new ArrayList<>(session.pendingClarification);
        session.pendingClarification.clear();
        session.clarificationIndex = 0;

        boolean semuaTanpaKec = all.stream().allMatch(o -> o.kecepatan == null);
        if (semuaTanpaKec) return masukModeBGlobal(all, "", session);

        for (ParsedOrder o : all) {
            if (o.isDowngraded) {
                session.downgradedLayanan.add(o.layanan.getNamaLayanan());
            }
            session.draftItems.add(toDraft(o));
        }

        return routeToNotaOrDowngrade(session);
    }

    private BotResponse nextClarification(ChatSession session) {
        session.clarificationIndex++;
        if (session.clarificationIndex < session.pendingClarification.size()) return startClarification(session);

        session.pendingClarification.clear();
        session.clarificationIndex = 0;
        return routeToNotaOrDowngrade(session);
    }

    // =========================================================================
    //  STATE: KONFIRMASI
    // =========================================================================
    private BotResponse handleKonfirmasi(String lower, ChatSession session) {
        if (has(lower,"ya","iya","yes","ok","oke","betul","benar","konfirmasi","setuju","pesan","lanjut"))
            return simpanPesanan(session);
        if (isBatal(lower)) { resetSemua(session); return txt("Pesanan dibatalkan. Ada yang bisa saya bantu?"); }
        if (has(lower,"ubah","ganti","edit")) {
            if (session.draftItems.size() == 1) {
                session.editIndex = 0;
                session.state = ConvState.PILIH_ASPEK_UBAH;
                return promptAspekUbah(session);
            }
            session.state = ConvState.PILIH_ITEM_UBAH;
            return promptPilihItemUbah(session);
        }
        if (has(lower,"ulang","salah semua")) { resetSemua(session); return txt("Oke, mari ulangi dari awal."); }

        return txt("Ketik ya → simpan  |  batal → batalkan  |  ubah → edit pesanan");
    }

    // =========================================================================
    //  STATE: UBAH / EDIT PESANAN
    // =========================================================================
    private BotResponse promptPilihItemUbah(ChatSession s) {
        StringBuilder sb = new StringBuilder("Pilih nomor item yang ingin diubah:\n");
        for (int i = 0; i < s.draftItems.size(); i++) {
            sb.append(i + 1).append(". ").append(s.draftItems.get(i).layanan.getNamaLayanan()).append("\n");
        }
        sb.append("\nKetik angkanya (contoh: 1), atau batal untuk kembali.");
        return txt(sb.toString());
    }

    private BotResponse handlePilihItemUbah(String raw, String lower, ChatSession session) {
        if (isBatal(lower)) { session.state = ConvState.KONFIRMASI; return tampilNota(session); }
        int idx = extractItem(lower); if (idx <= 0) idx = (int) extractBare(lower);
        if (idx < 1 || idx > session.draftItems.size()) return txt("Nomor item tidak valid. Pilih dari 1 sampai " + session.draftItems.size());

        session.editIndex = idx - 1;
        session.state = ConvState.PILIH_ASPEK_UBAH;
        return promptAspekUbah(session);
    }

    private BotResponse promptAspekUbah(ChatSession s) {
        ItemDraft d = s.draftItems.get(s.editIndex);
        return txt("Apa yang ingin diubah dari *" + d.layanan.getNamaLayanan() + "*?\n" +
                "1. Kuantitas (" + (d.layanan.isPerKg() ? "Berat" : "Jumlah Item") + ")\n" +
                "2. Kecepatan\n" +
                "3. Add-on\n" +
                "4. Hapus Item Ini\n\n" +
                "Ketik angka 1-4, atau batal.");
    }

    private BotResponse handlePilihAspekUbah(String raw, String lower, ChatSession session) {
        if (isBatal(lower)) { session.state = ConvState.KONFIRMASI; return tampilNota(session); }
        ItemDraft d = session.draftItems.get(session.editIndex);

        if (has(lower, "1", "kuantitas", "berat", "jumlah")) {
            session.state = ConvState.UBAH_KUANTITAS;
            return txt("Masukkan " + (d.layanan.isPerKg() ? "berat baru (kg):" : "jumlah item baru:"));
        }
        if (has(lower, "2", "kecepatan", "speed")) {
            session.state = ConvState.UBAH_KECEPATAN;
            return txt("Pilih kecepatan baru untuk " + d.layanan.getNamaLayanan() + ":\n- Standar\n- Express\n\nKetik standar atau express.");
        }
        if (has(lower, "3", "addon", "add-on", "tambahan")) {
            session.state = ConvState.UBAH_ADDON;
            return txt("Ketik add-on baru (ini akan menggantikan yang lama):\n" + buatOpsiAddon() + "Ketik nama add-on, atau 'tanpa add on' jika ingin dihapus.");
        }
        if (has(lower, "4", "hapus", "buang")) {
            session.draftItems.remove(session.editIndex);
            if (session.draftItems.isEmpty()) {
                resetSemua(session);
                return txt("Item telah dihapus. Karena tidak ada item tersisa, pesanan dibatalkan.");
            }
            session.state = ConvState.KONFIRMASI;
            return tampilNota(session);
        }
        return txt("Pilihan tidak valid. Ketik angka 1-4.");
    }

    private BotResponse handleUbahKuantitas(String raw, String lower, ChatSession session) {
        if (isBatal(lower)) { session.state = ConvState.KONFIRMASI; return tampilNota(session); }
        ItemDraft d = session.draftItems.get(session.editIndex);

        if (d.layanan.isPerKg()) {
            double kg = extractKg(lower); if (kg <= 0) kg = extractBare(lower);
            if (kg <= 0) return txt("Masukkan berat yang valid. Contoh: 2 kg");
            String err = validasiBerat(kg); if (err != null) return txt(err);
            d.beratKg = kg;
        } else {
            int item = extractItem(lower); if (item <= 0) item = (int) extractBare(lower);
            if (item <= 0) return txt("Masukkan jumlah yang valid. Contoh: 3");
            String err = validasiItem(item); if (err != null) return txt(err);
            d.jumlahItem = item;
        }

        recalcDraft(d);
        session.state = ConvState.KONFIRMASI;
        return tampilNota(session);
    }

    private BotResponse handleUbahKecepatan(String raw, String lower, ChatSession session) {
        if (isBatal(lower)) { session.state = ConvState.KONFIRMASI; return tampilNota(session); }
        ItemDraft d = session.draftItems.get(session.editIndex);

        if (PAT_EXPRESS.matcher(lower).find()) {
            if (!d.layanan.isBisaExpress()) return txt("Maaf, layanan ini tidak tersedia untuk Express. Ketik standar atau batal.");
            d.kecepatan = "EXPRESS";
            d.expressTotal = d.layanan.isPerKg() ? getExpressRate(d.layanan) * Math.max(d.beratKg, 1) : getExpressRate(d.layanan);
        } else if (PAT_STANDAR.matcher(lower).find()) {
            d.kecepatan = "STANDAR";
            d.expressTotal = 0;
        } else {
            return txt("Pilihan tidak dikenali. Ketik standar atau express.");
        }

        recalcDraft(d);
        session.state = ConvState.KONFIRMASI;
        return tampilNota(session);
    }

    private BotResponse handleUbahAddon(String raw, String lower, ChatSession session) {
        if (isBatal(lower)) { session.state = ConvState.KONFIRMASI; return tampilNota(session); }
        ItemDraft d = session.draftItems.get(session.editIndex);
        List<Layanan> addonsDb = layananService.getAddonAktif();

        if (PAT_TANPA_ADDON.matcher(lower).find()) {
            d.addonNama = "";
            d.addonTotal = 0;
        } else {
            List<String> namaBaru = new ArrayList<>();
            double totalBaru = 0;

            if (PAT_SEMUA_ADDON.matcher(lower).find()) {
                addonsDb.forEach(a -> { namaBaru.add(a.getNamaLayanan()); });
                totalBaru = addonsDb.stream().mapToDouble(Layanan::getHarga).sum();
            } else {
                for (Layanan a : addonsDb) {
                    if (addonDipilih(a.getNamaLayanan().toLowerCase(), lower)) {
                        namaBaru.add(a.getNamaLayanan());
                        totalBaru += a.getHarga();
                    }
                }
            }
            if (namaBaru.isEmpty()) return txt("Add-on tidak dikenali. Ketik ulang atau ketik 'tanpa add on'.");
            d.addonNama = String.join(", ", namaBaru);
            d.addonTotal = totalBaru;
        }

        recalcDraft(d);
        session.state = ConvState.KONFIRMASI;
        return tampilNota(session);
    }

    private void recalcDraft(ItemDraft d) {
        d.subtotalLayanan = d.layanan.isPerKg() ? d.layanan.getHarga() * d.beratKg : d.layanan.getHarga() * d.jumlahItem;
        // Update harga express dinamis (jika dihitung per-kg)
        if ("EXPRESS".equals(d.kecepatan) && d.layanan.isPerKg()) {
            d.expressTotal = getExpressRate(d.layanan) * Math.max(d.beratKg, 1);
        }
        d.totalItem = d.subtotalLayanan + d.expressTotal + d.addonTotal;
    }

    // =========================================================================
    //  HARGA
    // =========================================================================
    private ItemDraft toDraft(ParsedOrder o) {
        double expRate = "EXPRESS".equals(o.kecepatan) ? hitungExpressOrder(o) : 0;
        double sub     = o.layanan.isPerKg() ? o.layanan.getHarga() * o.beratKg : o.layanan.getHarga() * o.jumlahItem;
        double addT    = o.addonHarga.stream().mapToDouble(Double::doubleValue).sum();
        return new ItemDraft(o.layanan, o.beratKg, o.jumlahItem,
                o.kecepatan != null ? o.kecepatan : "STANDAR",
                expRate, String.join(", ", o.addonName), addT, sub, sub + expRate + addT);
    }

    private double getExpressRate(Layanan l) {
        return l.isPerKg()
                ? infoService.getNilai("ADDON_CONFIG","express_surcharge_per_kg").map(Double::parseDouble).orElse(5000d)
                : infoService.getNilai("ADDON_CONFIG","express_surcharge_flat").map(Double::parseDouble).orElse(15000d);
    }

    private double hitungExpressOrder(ParsedOrder o) {
        double rate = getExpressRate(o.layanan);
        return o.layanan.isPerKg() ? rate * Math.max(o.beratKg, 1) : rate;
    }

    private double hitungExpressSesi(ChatSession s) {
        double rate = getExpressRate(s.layanan);
        return s.layanan.isPerKg() ? rate * Math.max(s.beratKg, 1) : rate;
    }

    private double hitungSubtotalSesi(ChatSession s) {
        return s.layanan.isPerKg() ? s.layanan.getHarga() * s.beratKg : s.layanan.getHarga() * s.jumlahItem;
    }

    // =========================================================================
    //  NOTA & SIMPAN
    // =========================================================================
    private String ringkasanDraft(ChatSession s) {
        if (s.draftItems.isEmpty()) return "";
        StringBuilder sb = new StringBuilder("Item sejauh ini:\n");
        double grand = 0;
        int no = 1;
        for (ItemDraft d : s.draftItems) {
            sb.append(no++).append(". ").append(d.layanan.getNamaLayanan());
            if (d.layanan.isPerKg() && d.beratKg > 0) sb.append(" x ").append(d.beratKg).append(" kg");
            else if (!d.layanan.isPerKg() && d.jumlahItem > 0) sb.append(" x ").append(d.jumlahItem).append(" item");
            if ("EXPRESS".equals(d.kecepatan)) sb.append(" [Express]");
            if (!d.addonNama.isBlank()) sb.append(" + ").append(d.addonNama);
            sb.append("  → Rp").append(fmt(d.totalItem)).append("\n");
            grand += d.totalItem;
        }
        sb.append("Subtotal sementara: Rp").append(fmt(grand)).append("\n");
        return sb.toString();
    }

    // =========================================================================
    //  STATE: TANYA DOWNGRADE (Penjaga Gerbang)
    // =========================================================================
    private BotResponse routeToNotaOrDowngrade(ChatSession session) {
        if (!session.downgradedLayanan.isEmpty()) {
            session.state = ConvState.TANYA_DOWNGRADE;
            StringBuilder sb = new StringBuilder("Pemberitahuan: Layanan berikut tidak memiliki opsi Express:\n");
            for (String nama : session.downgradedLayanan) {
                sb.append("- ").append(nama).append("\n");
            }
            sb.append("\nApakah kamu tetap ingin memesan layanan tersebut dengan kecepatan Standar?\n");
            sb.append("- Ketik ya → lanjut dengan Standar\n");
            sb.append("- Ketik hapus → hapus layanan tersebut dari pesanan\n");
            sb.append("- Ketik batal → batalkan seluruh pesanan");
            return txt(sb.toString());
        }

        // Jika tidak ada yang di-downgrade, langsung lolos ke Nota
        session.state = ConvState.KONFIRMASI;
        return tampilNota(session);
    }

    private BotResponse handleTanyaDowngrade(String raw, String lower, ChatSession session) {
        if (isBatal(lower)) { resetSemua(session); return txt("Pesanan dibatalkan. Ada yang bisa saya bantu?"); }

        // Jika user tidak setuju dan ingin menghapus item tersebut
        if (has(lower, "hapus", "buang", "tidak", "cancel")) {
            session.draftItems.removeIf(d -> session.downgradedLayanan.contains(d.layanan.getNamaLayanan()));
            session.downgradedLayanan.clear();

            if (session.draftItems.isEmpty()) {
                resetSemua(session);
                return txt("Semua item yang tidak bisa Express telah dihapus. Pesanan kini kosong dan dibatalkan.\nSilakan pesan ulang.");
            }
            session.state = ConvState.KONFIRMASI;
            return tampilNota(session);
        }

        // Jika user pasrah dan setuju lanjut pakai standar
        if (has(lower, "ya", "iya", "lanjut", "oke", "ok", "tetap", "standar aja")) {
            session.downgradedLayanan.clear(); // Bersihkan agar tidak ditanya lagi
            session.state = ConvState.KONFIRMASI;
            return tampilNota(session);
        }

        return txt("Ketik ya untuk lanjut dengan Standar, ketik hapus untuk membatalkan item tersebut, atau batal.");
    }

    private BotResponse tampilNota(ChatSession s) {
        if (s.draftItems.isEmpty()) return txt("Tidak ada item. Mulai dengan memilih layanan.");
        StringBuilder sb = new StringBuilder("RINGKASAN PESANAN\n========================\n");
        double grand = 0;
        int no = 1;
        for (ItemDraft d : s.draftItems) {
            sb.append("ITEM ").append(no++).append(" — ").append(d.layanan.getNamaLayanan()).append("\n");
            if (d.layanan.isPerKg() && d.beratKg > 0)
                sb.append("  Rp").append(fmt(d.layanan.getHarga())).append("/kg x ").append(d.beratKg).append(" kg = Rp").append(fmt(d.subtotalLayanan)).append("\n");
            else if (!d.layanan.isPerKg() && d.jumlahItem > 0)
                sb.append("  Rp").append(fmt(d.layanan.getHarga())).append("/item x ").append(d.jumlahItem).append(" item = Rp").append(fmt(d.subtotalLayanan)).append("\n");
            if (d.expressTotal > 0) sb.append("  Express: +Rp").append(fmt(d.expressTotal)).append("\n");
            if (!d.addonNama.isBlank()) sb.append("  Add-on (").append(d.addonNama).append("): +Rp").append(fmt(d.addonTotal)).append("\n");
            sb.append("  Subtotal: Rp").append(fmt(d.totalItem)).append("  [").append(d.kecepatan).append("]\n\n");
            grand += d.totalItem;
        }
        sb.append("========================\nTOTAL: Rp").append(fmt(grand)).append("\n\n");
        sb.append("- Ketik ya    → konfirmasi & simpan\n- Ketik batal → batalkan\n- Ketik ubah  → ulangi");
        return txt(sb.toString());
    }

    private BotResponse simpanPesanan(ChatSession s) {
        if (s.draftItems.isEmpty()) return txt("Tidak ada item untuk disimpan.");
        double grand = s.draftItems.stream().mapToDouble(d -> d.totalItem).sum();
        String kode  = pesananService.simpanPesananDariChat(s.draftItems);
        StringBuilder sb = new StringBuilder("Pesanan berhasil disimpan!\n========================\n");
        sb.append("ID: ").append(kode).append("\nTanggal: ").append(LocalDate.now()).append("\n========================\n\n");
        int no = 1;
        for (ItemDraft d : s.draftItems) {
            sb.append("Item ").append(no++).append(": ").append(d.layanan.getNamaLayanan());
            if (d.layanan.isPerKg() && d.beratKg > 0) sb.append(" x ").append(d.beratKg).append(" kg");
            else if (!d.layanan.isPerKg() && d.jumlahItem > 0) sb.append(" x ").append(d.jumlahItem).append(" item");
            if ("EXPRESS".equals(d.kecepatan)) sb.append(" [Express]");
            if (!d.addonNama.isBlank()) sb.append(" + ").append(d.addonNama);
            sb.append("  → Rp").append(fmt(d.totalItem)).append("\n");
        }
        sb.append("========================\nTOTAL: Rp").append(fmt(grand)).append("\n\n");
        sb.append("Status: DITERIMA\nKode: ").append(kode).append(" untuk cek status.\nTerima kasih sudah menggunakan Washie!");
        resetSemua(s);
        return new BotResponse(sb.toString(), ResponseType.NOTA, kode);
    }

    // =========================================================================
    //  PROMPT HELPERS
    // =========================================================================
    private BotResponse promptKecepatan(ChatSession s) {
        double rate = getExpressRate(s.layanan);
        String sat  = s.layanan.isPerKg() ? "/kg" : "/item";
        return txt("Pilih kecepatan untuk " + s.layanan.getNamaLayanan() + ":\n- Standar : " + s.layanan.getEstimasiWaktu() + "\n- Express : 1 Hari Kerja (+Rp" + fmt(rate) + sat + ")\n\nKetik standar atau express");
    }

    private BotResponse promptAddon() {
        String opsi = buatOpsiAddon();
        if (opsi.isBlank()) return txt("Tidak ada produk tambahan tersedia.\n\nKetik lanjut untuk konfirmasi.");
        return txt("Apakah ingin menambahkan produk tambahan?\n\n" + opsi + "Ketik nama produk, atau ketik tidak jika tidak perlu.");
    }

    private String buatOpsiAddon() {
        List<Layanan> addons = layananService.getAddonAktif();
        if (addons.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        addons.forEach(a -> sb.append("- ").append(a.getNamaLayanan()).append("  +Rp").append(fmt(a.getHarga())).append("\n"));
        return sb.append("\n").toString();
    }

    // =========================================================================
    //  INFO HANDLERS
    // =========================================================================
    private BotResponse handleInfo(String tipe, String namaLayanan) {
        Optional<Layanan> opt = cariLayananDb(namaLayanan);
        if (opt.isEmpty()) return txt("Maaf, informasi " + namaLayanan + " belum tersedia.");
        Layanan l = opt.get(); String sat = l.isPerKg() ? "/kg" : "/item"; double er = getExpressRate(l);
        return switch (tipe) {
            case "HARGA" -> txt("Harga " + l.getNamaLayanan() + ":\n- Standar: Rp" + fmt(l.getHarga()) + sat + " (" + l.getEstimasiWaktu() + ")\n- Express: " + (l.isBisaExpress() ? "+Rp" + fmt(er) + sat + " (1 Hari)" : "tidak tersedia") + "\n\nContoh: 3 " + (l.isPerKg() ? "kg" : "item") + " x Rp" + fmt(l.getHarga()) + " = Rp" + fmt(l.getHarga() * 3) + "\n\nKetik: " + l.getNamaLayanan().toLowerCase() + (l.isPerKg() ? " 2 kg" : " 1 item"));
            case "ESTIMASI" -> txt("Estimasi " + l.getNamaLayanan() + ":\n- Standar: " + l.getEstimasiWaktu() + "\n- Express: " + (l.isBisaExpress() ? "1 Hari Kerja (+Rp" + fmt(er) + sat + ")" : "tidak tersedia") + "\n\nHarga: Rp" + fmt(l.getHarga()) + sat);
            case "DESKRIPSI" -> {
                Map<String,String> d = new HashMap<>(Map.of("Dry Cleaning","Pencucian cairan kimia khusus. Cocok jas, kebaya, gaun, wol, sutra.","Cuci Kering","Cuci+kering tanpa setrika. Untuk pakaian kasual.","Cuci + Setrika","Cuci, kering, setrika rapi. Untuk kemeja dan formal.","Setrika Saja","Penyetrikaan pakaian yang sudah bersih.","Cuci Bedcover","Pencucian bedcover mesin besar. Per item.","Cuci Boneka","Pencucian lembut stuffed toy. Per item."));
                d.put("Cuci Karpet","Pencucian karpet mesin khusus. Per kg.");
                d.put("Cuci Selimut","Pencucian selimut tebal/tipis. Per item.");
                d.put("Cuci Handuk","Pencucian handuk menjaga kelembutan.");
                d.put("Cuci Gorden & Vitrase","Pencucian tirai/gorden hati-hati. Per item.");
                d.put("Cuci Sprei & Sarung Bantal","Pencucian sprei+sarung bantal. Per set.");
                yield txt(l.getNamaLayanan() + "\n\n" + d.getOrDefault(l.getNamaLayanan(),"Layanan pencucian profesional.") + "\n\nHarga: Rp" + fmt(l.getHarga()) + sat + " | Estimasi: " + l.getEstimasiWaktu() + "\nExpress: " + (l.isBisaExpress() ? "tersedia (+Rp" + fmt(er) + sat + ")" : "tidak tersedia"));
            }
            default -> respFallback();
        };
    }

    // =========================================================================
    //  RESPONS UMUM
    // =========================================================================
    private BotResponse respTanyaLayanan() {
        List<Layanan> aktif = layananService.getLayananAktif();
        if (aktif.isEmpty()) return txt("Belum ada layanan aktif. Hubungi WA kami.");
        StringBuilder sb = new StringBuilder("Mau laundry apa?\n\n");
        aktif.stream().filter(Layanan::isPerKg).forEach(l -> sb.append(String.format("- %-28s Rp%s/kg\n", l.getNamaLayanan(), fmt(l.getHarga()))));
        List<Layanan> pi = aktif.stream().filter(l -> !l.isPerKg()).collect(Collectors.toList());
        if (!pi.isEmpty()) { sb.append("\nPer Item:\n"); pi.forEach(l -> sb.append(String.format("- %-28s Rp%s/item\n", l.getNamaLayanan(), fmt(l.getHarga())))); }
        sb.append("\nContoh pesan:\n  cuci setrika 3kg, cuci boneka 2 item\n  (kecepatan & add-on boleh disebutkan setelahnya)");
        return txt(sb.toString());
    }

    private BotResponse respDaftarLayanan() { return respTanyaLayanan(); }
    private BotResponse cekStatus(String kode) {
        return pesananService.getByKode(kode).map(p -> txt("Status " + p.getKodePesanan() + ":\nNama: " + p.getUser().getNamaLengkap() + "\nLayanan: " + (p.getLayanan() != null ? p.getLayanan().getNamaLayanan() : "-") + (p.getItems().size() > 1 ? " +" + (p.getItems().size()-1) + " lagi" : "") + "\nTotal: Rp" + (p.getTotalHarga() != null ? fmt(p.getTotalHarga()) : "-") + "\nStatus: " + switch(p.getStatus()){case BELUM_DIPROSES -> "📥 Belum Diproses (Menunggu Admin)"; case DIPROSES -> "Sedang Diproses"; case SELESAI -> "Selesai - siap diambil!"; case DIAMBIL -> "Sudah Diambil"; case DIBATALKAN -> "❌ Dibatalkan";})).orElse(txt("Pesanan " + kode + " tidak ditemukan."));
    }

    private BotResponse handleBatalkanPesanan(String raw, String lower){
        String kode = cariKode(raw); // Menggunakan method cariKode yang sudah kamu punya

        if (kode == null) {
            return txt("Masukkan kode pesanan yang ingin dibatalkan.\nContoh: batalkan WS-001");
        }
        String hasil = pesananService.batalkanPesananUser(kode);
        return switch (hasil) {
            case "NOT_FOUND" -> txt("Pesanan " + kode + " tidak ditemukan.");
            case "UNAUTHORIZED" -> txt("Kamu tidak bisa membatalkan pesanan milik akun lain.");
            case "TOLAK" -> txt("Maaf, pesanan " + kode + " sudah mulai diproses (atau sudah selesai) sehingga tidak bisa dibatalkan.");
            case "SUKSES" -> txt("Sip! Pesanan " + kode + " berhasil dibatalkan.");
            default -> txt("Terjadi kesalahan sistem. Silakan hubungi admin.");
        };
    }

    private BotResponse cekRiwayat() {
        List<Pesanan> riwayat = pesananService.getRiwayatPesananCurrentUser();

        if (riwayat.isEmpty()) {
            return txt("Kamu belum memiliki riwayat pesanan. Yuk buat pesanan pertamamu!");
        }
        StringBuilder sb = new StringBuilder("Riwayat Pesanan Kamu:\n\n");
        int limit = Math.min(riwayat.size(), 5);
        for (int i = 0; i < limit; i++) {
            Pesanan p = riwayat.get(i);
            String status = switch(p.getStatus()) {
                case BELUM_DIPROSES -> "📥 Belum Diproses (Menunggu Admin)";
                case DIPROSES -> "⏳ Diproses";
                case SELESAI -> "✅ Selesai";
                case DIAMBIL -> "🛍️ Diambil";
                case DIBATALKAN -> "❌ Dibatalkan";
                default -> p.getStatus().name();
            };

            sb.append("- ").append(p.getKodePesanan())
                    .append(" | Rp").append(fmt(p.getTotalHarga()))
                    .append(" | ").append(status)
                    .append("\n");
        }

        if (riwayat.size() > 5) {
            sb.append("\n(Cek menu dashboard untuk riwayat selengkapnya)");
        }

        return txt(sb.toString());
    }
    private BotResponse respJam() {
        return txt("Jam Operasional:\nSenin-Jumat  : " + infoService.getNilai("JAM_OPERASIONAL","senin_jumat").orElse("08.00-21.00") + "\nSabtu-Minggu : " + infoService.getNilai("JAM_OPERASIONAL","sabtu_minggu").orElse("09.00-19.00") + "\nHari Libur   : " + infoService.getNilai("JAM_OPERASIONAL","hari_libur").orElse("Tutup"));
    }
    private BotResponse respLokasi() {
        return txt("Lokasi & Kontak:\nAlamat    : " + infoService.getNilai("LOKASI_KONTAK","alamat").orElse("-") + "\nWhatsApp  : " + infoService.getNilai("LOKASI_KONTAK","whatsapp").orElse("-") + "\nInstagram : " + infoService.getNilai("LOKASI_KONTAK","instagram").orElse("-"));
    }
    private BotResponse respPengumuman() {
        List<InfoEntity> list = infoService.getPengumuman();
        if (list.isEmpty()) return txt("Belum ada pengumuman. Hubungi WA untuk info lebih lanjut.");
        StringBuilder sb = new StringBuilder("Info terbaru:\n\n");
        list.forEach(p -> sb.append("[").append(p.getKunci()).append("]\n").append(p.getNilai()).append("\n\n"));
        return txt(sb.toString().trim());
    }

    private BotResponse salam() {
        StringBuilder sb = new StringBuilder("Halo! Selamat datang di Washie Laundry.\n\n");
        List<InfoEntity> peng = infoService.getPengumuman();
        if (!peng.isEmpty()) { sb.append("--- Info dari Washie ---\n"); peng.forEach(p -> sb.append("[").append(p.getKunci()).append("]\n").append(p.getNilai()).append("\n\n")); sb.append("------------------------\n\n"); }
        sb.append("Cara pesan:\n");
        sb.append("  Bubble 1: cuci setrika 3kg, cuci boneka 2 item\n");
        sb.append("  Bubble 2: semua express, pewangi premium\n\n");
        sb.append("  Atau sekaligus:\n");
        sb.append("  cuci kering 2kg express pewangi, dry cleaning 5kg standar semua addon\n\n");
        sb.append("Ketik daftar layanan untuk lihat semua layanan.");
        return txt(sb.toString());
    }

    // =========================================================================
    //  EASTER EGGS — kata rahasia 🥚
    // =========================================================================
    private BotResponse cekEasterEgg(String raw, String lower) {

        // ── 1. SIAPA KAMU / WHO ARE YOU ──────────────────────────────────
        if (has(lower,"siapa kamu","siapa washie","kamu siapa","who are you","kamu itu apa")) {
            return txt(
                    "Saya Washie, asisten virtual laundry yang siap membantu 24/7!\n\n" +
                            "Sedikit rahasia: saya dibuat dengan penuh cinta oleh tim developer muda " +
                            "dari UKDW Yogyakarta sebagai proyek RPLBO.\n\n" +
                            "Di balik layar, saya ditenagai oleh:\n" +
                            "  JavaFX    → tampilan yang kamu lihat\n" +
                            "  Spring Boot → otak yang berpikir\n" +
                            "  SQLite    → memori tempat saya menyimpan segalanya\n\n" +
                            "Saya memang hanya bot, tapi saya berusaha menjadi bot terbaik untuk Washie! 🧺✨"
            );
        }

        // ── 2. KATA RAHASIA: "agung" (nama pemilik laundry) ─────────────
        if (lower.equals("agung") || lower.equals("pak agung") || lower.equals("bapak agung")) {
            return txt(
                    "🤫 Ssst... kamu tahu nama pemilik Washie!\n\n" +
                            "Halo dari Washie Assistant!\n" +
                            "Bapak Agung Prayono adalah founder Washie Laundry sejak 2020.\n" +
                            "Di bawah kepemimpinan beliau, Washie terus berkembang melayani pelanggan setia.\n\n" +
                            "Terima kasih sudah mengenal kami lebih dekat! 🧺💛"
            );
        }

        // ── 3. KATA RAHASIA: "washie123" ─────────────────────────────────
        if (lower.equals("washie123")) {
            return txt(
                    "🎉 KODE RAHASIA DITERIMA!\n\n" +
                            "Selamat! Kamu berhasil menemukan easter egg tersembunyi di Washie Bot.\n\n" +
                            "Fun fact tentang Washie:\n" +
                            "  🧺 Kami mencuci lebih dari 1.000 kg pakaian setiap bulan\n" +
                            "  ⏱️  Layanan tercepat kami: Express 1 Hari Kerja\n" +
                            "  🌸  Add-on favorit pelanggan: Pewangi Premium\n" +
                            "  💛  Warna kebanggaan kami: Biru & Kuning\n\n" +
                            "Terima kasih sudah menjadi bagian dari keluarga Washie! 🎊"
            );
        }

        // ── 4. KATA RAHASIA: "rplbo" (mata kuliah) ───────────────────────
        if (has(lower,"rplbo","rekayasa perangkat lunak")) {
            return txt(
                    "🎓 Hei, kamu pasti mahasiswa UKDW!\n\n" +
                            "Washie Bot adalah proyek RPLBO (Rekayasa Perangkat Lunak Berbasis Objek).\n\n" +
                            "Tech stack yang dipakai:\n" +
                            "  ☕ Java 21         → bahasa pemrograman\n" +
                            "  🖥️  JavaFX 21      → antarmuka grafis\n" +
                            "  🌱 Spring Boot 3  → framework backend\n" +
                            "  🗄️  Spring Data JPA → akses database\n" +
                            "  🪶 SQLite          → database ringan\n" +
                            "  🤖 Rule-based NLP  → otak chatbot ini\n\n" +
                            "Semoga nilainya bagus ya! 🍀"
            );
        }

        // ── 5. KATA RAHASIA: "ukdw" ───────────────────────────────────────
        if (lower.equals("ukdw") || has(lower,"universitas kristen duta wacana")) {
            return txt(
                    "🏫 UKDW — Universitas Kristen Duta Wacana, Yogyakarta!\n\n" +
                            "Washie Bot lahir dari bangku kuliah di kampus tercinta ini.\n" +
                            "Dikembangkan dengan dedikasi dan banyak begadang ☕\n\n" +
                            "\"Dari Yogyakarta untuk Indonesia — bersih, rapi, dan tepat waktu.\"\n\n" +
                            "Bangga jadi karya mahasiswa UKDW! 💙"
            );
        }

        // ── 6. KATA RAHASIA: "dev mode" ──────────────────────────────────
        if (has(lower,"dev mode","developer mode","mode developer","debug")) {
            List<Layanan> semua  = layananService.getAll();
            long layananCount    = semua.stream().filter(l -> l.getTipe() == Layanan.Tipe.LAYANAN).count();
            long addonCount      = semua.stream().filter(l -> l.getTipe() == Layanan.Tipe.ADDON).count();
            long pengumuman      = infoService.getPengumuman().size();
            return txt(
                    "🔧 DEV MODE AKTIF\n\n" +
                            "Status sistem:\n" +
                            "  Layanan aktif : " + layananCount + " layanan\n" +
                            "  Add-on aktif  : " + addonCount + " addon\n" +
                            "  Pengumuman    : " + pengumuman + " aktif\n\n" +
                            "States tersedia:\n" +
                            "  IDLE → TANYA_BERAT/JUMLAH → PILIH_KECEPATAN\n" +
                            "  → PILIH_ADDON → TANYA_TAMBAH_ITEM → KONFIRMASI\n" +
                            "  → CLARIFICATION → TANYA_SPEED_ADDON_GLOBAL\n\n" +
                            "Build: Washie Bot v1.0 | Spring Boot 3 | JavaFX 21\n" +
                            "Status: RUNNING ✅"
            );
        }

        // ── 7. KATA RAHASIA: "cuci otak" (humor) ─────────────────────────
        if (has(lower,"cuci otak","laundry otak","cuci pikiran")) {
            return txt(
                    "🧠 Waduh, sayangnya kami tidak menyediakan layanan Cuci Otak!\n\n" +
                            "Tapi tenang, untuk pakaian kamu:\n" +
                            "  🧺 Cuci Kering   → Rp5.000/kg\n" +
                            "  👔 Cuci + Setrika → Rp7.000/kg\n" +
                            "  ⚡ Express       → selesai 1 hari!\n\n" +
                            "Mau pesan? Ketik nama layanan + berat/jumlah 😄"
            );
        }

        // ── 8. KONAMI CODE (text version): "atas atas bawah bawah" ──────
        if (has(lower,"atas atas bawah bawah kiri kanan kiri kanan")) {
            return txt(
                    "🎮 KONAMI CODE DETECTED!\n\n" +
                            "↑ ↑ ↓ ↓ ← → ← → B A\n\n" +
                            "Selamat! Kamu membuka cheat mode Washie:\n\n" +
                            "✨ DISKON IMAJINER 99% AKTIF ✨\n" +
                            "(Sayangnya ini hanya easter egg, bukan diskon sungguhan 😂)\n\n" +
                            "Tapi benerannya, Washie tetap memberikan harga terbaik!\n" +
                            "Cek daftar layanan kami untuk harga yang nyata 🧺"
            );
        }

        // ── 9. KATA RAHASIA: "kapan selesai" tanpa konteks pesanan ───────
        if (lower.equals("kapan selesai") || lower.equals("udah selesai belum")) {
            return txt(
                    "😅 Kamu tanya ke saya, tapi saya tidak tahu kamu sedang menunggu pesanan yang mana!\n\n" +
                            "Untuk cek status pesanan, ketik kode pesanan kamu.\n" +
                            "Contoh: cek WS-001\n\n" +
                            "Tidak ingat kodenya? Cek struk atau tanya langsung ke WhatsApp kami 📱"
            );
        }

        // ── 10. KATA RAHASIA: "i love washie" ────────────────────────────
        if (has(lower,"i love washie","aku suka washie","saya suka washie","love washie","washie terbaik","washie the best")) {
            return txt(
                    "💛 Aww, Washie juga sayang kamu!\n\n" +
                            "Terima kasih sudah mencintai Washie Laundry.\n" +
                            "Kami berjanji akan terus memberikan pelayanan terbaik:\n" +
                            "  ✅ Bersih hingga ke serat terdalam\n" +
                            "  ✅ Rapi hingga ke lipatan terakhir\n" +
                            "  ✅ Wangi hingga ke hari berikutnya\n" +
                            "  ✅ Tepat waktu sesuai estimasi\n\n" +
                            "Mau laundry sekarang? Ketik nama layanan + berat! 🧺💛"
            );
        }

        return null; // bukan easter egg
    }

    private BotResponse respFallback() {
        return txt("Maaf, saya belum mengerti.\n\nCoba:\n- cuci setrika 3kg, cuci boneka 2 item\n  lalu saya tanya kecepatan & add-on\n- cek WS-001 : status pesanan\n- daftar layanan : semua layanan");
    }

    // =========================================================================
    //  UTILS
    // =========================================================================
    private Optional<Layanan> cariLayananDb(String term) {
        List<Layanan> aktif = layananService.getLayananAktif();
        String kw = term.toLowerCase();
        for (Layanan l : aktif) if (l.getNamaLayanan().equalsIgnoreCase(term)) return Optional.of(l);
        for (Layanan l : aktif) if (l.getNamaLayanan().toLowerCase().contains(kw)) return Optional.of(l);
        for (String w : kw.split("\\s+")) { if (w.length() < 3) continue; for (Layanan l : aktif) if (l.getNamaLayanan().toLowerCase().contains(w)) return Optional.of(l); }
        return Optional.empty();
    }

    private double extractKg(String s) {
        Matcher m = PAT_KG.matcher(s);
        while (m.find()) { try { double v = Double.parseDouble(m.group(1).replace(",",".")); if (v > 0) return v; } catch (NumberFormatException ignored) {} }
        return 0;
    }

    private int extractItem(String s) {
        Matcher m = PAT_ITEM.matcher(s);
        while (m.find()) { try { double v = Double.parseDouble(m.group(1).replace(",",".")); if (v > 0 && v == Math.floor(v)) return (int) v; } catch (NumberFormatException ignored) {} }
        return 0;
    }

    private double extractBare(String s) {
        if (extractKg(s) > 0 || extractItem(s) > 0) return 0;
        Matcher m = Pattern.compile("(?<![\\d.,])([1-9]\\d*(?:[.,]\\d+)?)(?![\\d.,])").matcher(s);
        while (m.find()) { try { double v = Double.parseDouble(m.group(1).replace(",",".")); if (v > 0 && v < 10000) return v; } catch (NumberFormatException ignored) {} }
        return 0;
    }

    private String cariKode(String s) {
        Matcher m = Pattern.compile("\\b(WS-\\d+)\\b", Pattern.CASE_INSENSITIVE).matcher(s);
        return m.find() ? m.group(1).toUpperCase() : null;
    }

    private boolean addonDipilih(String namaAddon, String userInput) {
        if (userInput.contains(namaAddon)) return true;
        if (namaAddon.equals("extra softener")) return (has(userInput,"extra softener","softener","pelembut")) && !has(userInput,"anti","antiseptik","septik","kuman");
        if (namaAddon.equals("anti-septik") || namaAddon.equals("anti septik")) return has(userInput,"anti-septik","anti septik","antiseptik","kuman","antibakteri") && !has(userInput,"softener","pelembut");
        if (namaAddon.contains("softener") && namaAddon.contains("anti")) return (has(userInput,"softener") && has(userInput,"anti","antiseptik","septik")) || has(userInput,"softener anti","softener+anti");
        String[] words = namaAddon.split("[\\s\\-+/]+");
        long req = Arrays.stream(words).filter(w -> w.length() >= 4).count(); if (req == 0) return false;
        long match = Arrays.stream(words).filter(w -> w.length() >= 4 && userInput.contains(w)).count();
        return match == req;
    }

    private boolean has(String s, String... kws) { for (String k : kws) if (s.contains(k)) return true; return false; }
    private boolean isBatal(String s) { return has(s,"batal","cancel","tidak jadi","ga jadi","gak jadi"); }
    private String fmt(double v) { return v == Math.floor(v) ? String.format("%.0f", v) : String.format("%.1f", v); }

    private void resetItem(ChatSession s) { s.layanan=null; s.beratKg=0; s.jumlahItem=0; s.kecepatan=null; s.expressTotal=0; s.addonNama.clear(); s.addonHarga.clear(); }
    private void resetSemua(ChatSession s) { resetItem(s); s.draftItems.clear(); s.pendingSpeedAddon.clear(); s.pendingClarification.clear(); s.clarificationIndex=0; s.downgradedLayanan.clear(); s.state=ConvState.IDLE; s.editIndex = -1;}

    private static BotResponse txt(String msg) { return new BotResponse(msg, ResponseType.TEXT, null); }
    public enum ResponseType { TEXT, LAYANAN, NOTA }
    public static class BotResponse {
        private final String text; private final ResponseType type; private final Object data;
        public BotResponse(String t, ResponseType r, Object d) { text=t; type=r; data=d; }
        public String getText() { return text; }
        public ResponseType getType() { return type; }
        public Object getData() { return data; }
    }
}
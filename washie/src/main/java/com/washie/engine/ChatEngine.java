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

    // ========================================================================
    // VALIDASI
    // ========================================================================
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
        TANYA_DOWNGRADE
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
        };
    }

    // =========================================================================
    //  STATE: IDLE
    // =========================================================================
    private BotResponse handleIdle(String raw, String lower, ChatSession session) {
        String kode = cariKode(raw);
        if (kode != null) return cekStatus(kode);
        if (has(lower,"status pesanan","cek pesanan","lacak")) return txt("Masukkan kode pesanan.\nContoh: cek WS-001");

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
    //  TANYA BERAT
    // =========================================================================
    private BotResponse handleTanyaBerat(String raw, String lower, ChatSession session) {
        if (has(lower,"batal","cancel","tidak jadi","ga jadi")) {
            reset(session); return txt("Pesanan dibatalkan. Ada yang bisa saya bantu?");
        }
        double berat = extractBerat(raw);
        if (berat <= 0) return txt("Masukkan berat dalam kg.\nContoh: 2 kg atau 1.5 kg");
        session.berat = berat;
        if (session.layanan.isBisaExpress()) {
            session.state = ConvState.PILIH_KECEPATAN; return promptKecepatan(session);
        }
        session.kecepatan = "STANDAR"; session.state = ConvState.PILIH_ADDON;
        return promptAddon();
    }

    // =========================================================================
    //  PILIH KECEPATAN
    // =========================================================================
    private BotResponse handlePilihKecepatan(String lower, ChatSession session) {
        if (has(lower,"batal","cancel")) { reset(session); return txt("Pesanan dibatalkan."); }
        if (has(lower,"express","ekspres","cepat","kilat","1 hari","sehari")) {
            session.kecepatan    = "EXPRESS";
            session.expressHarga = getExpressSurchargeTotal(session);
            session.state = ConvState.PILIH_ADDON; return promptAddon();
        }
        if (has(lower,"standar","standard","biasa","normal","reguler")) {
            session.kecepatan = "STANDAR"; session.expressHarga = 0;
            session.state = ConvState.PILIH_ADDON; return promptAddon();
        }
        return promptKecepatan(session);
    }

    // =========================================================================
    //  PILIH ADDON — addon dari tabel layanan (tipe=ADDON)
    // =========================================================================
    private BotResponse handlePilihAddon(String lower, ChatSession session) {
        if (has(lower,"batal","cancel")) { reset(session); return txt("Pesanan dibatalkan."); }
        if (has(lower,"tidak","tidak perlu","no","gak","ga","tanpa","skip","langsung","udah","lanjut")) {
            session.state = ConvState.KONFIRMASI; return tampilNota(session);
        }

        List<Layanan> addons = layananService.getAddonAktif();
        boolean ada = false;

        if (has(lower,"keduanya","semua","all","both")) {
            for (Layanan a : addons)
                if (!session.addonNama.contains(a.getNamaLayanan())) {
                    session.addonNama.add(a.getNamaLayanan()); session.addonHarga.add(a.getHarga()); ada = true;
                }
        } else {
            for (Layanan a : addons) {
                if (session.addonNama.contains(a.getNamaLayanan())) continue;

                // Fix #2: match berdasarkan NAMA LENGKAP addon (exact atau near-exact),
                // BUKAN substring kata — agar "pewangi premium" tidak trigger "pewangi luxury"
                boolean dipilih = addonDipilih(a.getNamaLayanan().toLowerCase(), lower);

                if (dipilih) {
                    session.addonNama.add(a.getNamaLayanan()); session.addonHarga.add(a.getHarga()); ada = true;
                }
            }
        }

        if (ada) { session.state = ConvState.KONFIRMASI; return tampilNota(session); }
        return txt("Maaf, saya tidak mengenali pilihannya.\n\n" + opsiAddon());
    }

    /**
     * Fix #2: Menentukan apakah user memilih addon tertentu.
     * Matching dilakukan per nama lengkap addon, bukan per kata tunggal.
     * Ini mencegah "pewangi premium" juga memilih "pewangi luxury".
     */
    private boolean addonDipilih(String namaAddon, String userInput) {
        // 1. Exact match nama lengkap
        if (userInput.contains(namaAddon)) return true;

        // 2. Match khusus per kategori addon yang sudah diketahui
        // Softener saja (tanpa anti)
        if (namaAddon.equals("extra softener"))
            return has(userInput,"extra softener","extra softner") ||
                    (has(userInput,"softener","pelembut") && !has(userInput,"anti","antiseptik","septik","kuman"));

        // Anti-septik saja (tanpa softener)
        if (namaAddon.equals("anti-septik") || namaAddon.equals("anti septik"))
            return has(userInput,"anti-septik","anti septik","antiseptik") ||
                    (has(userInput,"kuman","antibakteri","septik") && !has(userInput,"softener","pelembut"));

        // Paket softener+anti (harus sebut KEDUANYA)
        if (namaAddon.contains("softener") && namaAddon.contains("anti"))
            return (has(userInput,"softener") && has(userInput,"anti","antiseptik","septik")) ||
                    has(userInput,"softener anti","softener+anti","paket softener");

        // 3. Untuk addon lain (misal pewangi premium, pewangi luxury, dll):
        //    user harus menyebut NAMA LENGKAP atau frasa yang cukup unik.
        //    Ambil semua kata >= 4 huruf dari nama addon, semua harus ada di input user.
        String[] words = namaAddon.split("[\\s\\-+]+");
        int matchCount = 0;
        int required   = 0;
        for (String w : words) {
            if (w.length() >= 4) {
                required++;
                if (userInput.contains(w)) matchCount++;
            }
        }
        // Semua kata signifikan harus cocok (bukan hanya sebagian)
        return required > 0 && matchCount == required;
    }

    // =========================================================================
    //  KONFIRMASI
    // =========================================================================
    private BotResponse handleKonfirmasi(String lower, ChatSession session) {
        if (has(lower,"ya","iya","yes","ok","oke","betul","benar","konfirmasi","lanjut","setuju","pesan"))
            return simpanPesanan(session);
        if (has(lower,"tidak","batal","cancel","no","gak","ga")) {
            reset(session); return txt("Pesanan dibatalkan. Ada yang bisa saya bantu?");
        }
        if (has(lower,"ubah","ganti","edit","salah","ulang")) {
            reset(session); return txt("Oke, mari ulangi.\nKetik kembali kebutuhan laundry Anda.");
        }
        return txt("Mohon konfirmasi:\n- Ketik ya untuk konfirmasi\n- Ketik batal untuk membatalkan\n- Ketik ubah untuk mengulang");
    }

    // =========================================================================
    //  FLOW HELPERS
    // =========================================================================
    private BotResponse mulaiOrder(Layanan layanan, double berat, ChatSession session) {
        session.layanan = layanan;
        if (!layanan.isPerKg()) {
            if (layanan.isBisaExpress()) { session.state = ConvState.PILIH_KECEPATAN; return promptKecepatan(session); }
            session.kecepatan = "STANDAR"; session.state = ConvState.PILIH_ADDON; return promptAddon();
        }
        if (berat > 0) {
            session.berat = berat;
            if (layanan.isBisaExpress()) { session.state = ConvState.PILIH_KECEPATAN; return promptKecepatan(session); }
            session.kecepatan = "STANDAR"; session.state = ConvState.PILIH_ADDON; return promptAddon();
        }
        session.state = ConvState.TANYA_BERAT;
        return txt("Layanan " + layanan.getNamaLayanan() + " dipilih.\n\nBerapa berat pakaiannya? (dalam kg)\nContoh: 2 kg atau 1.5 kg");
    }

    private BotResponse promptKecepatan(ChatSession session) {
        double surge  = getExpressSurchargeRate(session.layanan);
        String satuan = session.layanan.isPerKg() ? "/kg" : "/item";
        return txt("Pilih kecepatan layanan:\n\n" +
                "- Standar : " + session.layanan.getEstimasiWaktu() + " (harga normal)\n" +
                "- Express : 1 Hari Kerja (+Rp" + String.format("%.0f", surge) + satuan + ")\n\n" +
                "Ketik standar atau express");
    }

    private BotResponse promptAddon() {
        String opsi = opsiAddon();
        if (opsi.isBlank()) {
            // Tidak ada addon → langsung konfirmasi
            return txt("Apakah ingin menambahkan produk tambahan?\nSaat ini belum ada produk tambahan tersedia.\n\nKetik lanjut untuk konfirmasi.");
        }
        return txt("Apakah ingin menambahkan produk tambahan?\n\n" + opsi +
                "Ketik nama produk yang diinginkan, atau ketik tidak jika tidak perlu.");
    }

    private String opsiAddon() {
        List<Layanan> addons = layananService.getAddonAktif();
        if (addons.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (Layanan a : addons)
            sb.append("- ").append(a.getNamaLayanan())
                    .append("  +Rp").append(String.format("%.0f", a.getHarga())).append("\n");
        sb.append("\n");
        return sb.toString();
    }

    private double getExpressSurchargeRate(Layanan l) {
        if (l.isPerKg())
            return infoService.getNilai("ADDON_CONFIG","express_surcharge_per_kg").map(Double::parseDouble).orElse(5000d);
        return infoService.getNilai("ADDON_CONFIG","express_surcharge_flat").map(Double::parseDouble).orElse(15000d);
    }

    private double getExpressSurchargeTotal(ChatSession s) {
        double rate = getExpressSurchargeRate(s.layanan);
        return s.layanan.isPerKg() ? rate * Math.max(s.berat, 1) : rate;
    }

    private BotResponse tampilNota(ChatSession s) {
        double subtotal   = s.layanan.isPerKg() ? s.layanan.getHarga() * s.berat : s.layanan.getHarga();
        double express    = "EXPRESS".equals(s.kecepatan) ? getExpressSurchargeTotal(s) : 0;
        double addonTotal = s.addonHarga.stream().mapToDouble(Double::doubleValue).sum();
        double total      = subtotal + express + addonTotal;

        StringBuilder sb = new StringBuilder();
        sb.append("RINGKASAN PESANAN\n");
        sb.append("------------------------\n");
        sb.append("Layanan    : ").append(s.layanan.getNamaLayanan()).append("\n");
        if (s.layanan.isPerKg()) sb.append("Berat      : ").append(s.berat).append(" kg\n");
        sb.append("Kecepatan  : ").append(s.kecepatan).append("\n");
        if (!s.addonNama.isEmpty()) {
            sb.append("Tambahan   :\n");
            for (int i = 0; i < s.addonNama.size(); i++)
                sb.append("  - ").append(s.addonNama.get(i))
                        .append(" (+Rp").append(String.format("%.0f", s.addonHarga.get(i))).append(")\n");
        }
        sb.append("------------------------\n");
        sb.append("Layanan    : Rp").append(String.format("%.0f", subtotal));
        if (s.layanan.isPerKg())
            sb.append(" (Rp").append(String.format("%.0f", s.layanan.getHarga())).append("/kg x ").append(s.berat).append(" kg)");
        sb.append("\n");
        if (express > 0)    sb.append("Express    : +Rp").append(String.format("%.0f", express)).append("\n");
        if (addonTotal > 0) sb.append("Tambahan   : +Rp").append(String.format("%.0f", addonTotal)).append("\n");
        sb.append("------------------------\n");
        sb.append("TOTAL      : Rp").append(String.format("%.0f", total)).append("\n\n");
        sb.append("Apakah pesanan sudah benar?\n");
        sb.append("- Ketik ya untuk konfirmasi\n- Ketik batal untuk membatalkan\n- Ketik ubah untuk mengulang");
        return txt(sb.toString());
    }

    private BotResponse simpanPesanan(ChatSession s) {
        double subtotal   = s.layanan.isPerKg() ? s.layanan.getHarga() * s.berat : s.layanan.getHarga();
        double express    = "EXPRESS".equals(s.kecepatan) ? getExpressSurchargeTotal(s) : 0;
        double addonTotal = s.addonHarga.stream().mapToDouble(Double::doubleValue).sum();
        double total      = subtotal + express + addonTotal;
        String estimasi   = "EXPRESS".equals(s.kecepatan) ? "1 Hari Kerja" : s.layanan.getEstimasiWaktu();
        String addonNamaStr = String.join(", ", s.addonNama);

        String kode = pesananService.simpanPesananDariChat(s.layanan, s.berat, total, s.kecepatan + (express > 0 ? " (+express Rp" + String.format("%.0f", express) + ")" : ""), addonNamaStr, subtotal, addonTotal);

        StringBuilder sb = new StringBuilder();
        sb.append("Pesanan berhasil disimpan!\n\n");
        sb.append("------------------------\n");
        sb.append("ID Pesanan   : ").append(kode).append("\n");
        sb.append("Tanggal      : ").append(LocalDate.now()).append("\n");
        sb.append("------------------------\n");
        sb.append(s.layanan.getNamaLayanan());
        if (s.layanan.isPerKg()) sb.append(" x ").append(s.berat).append(" kg");
        sb.append("\n");
        if (express > 0) sb.append("Express Service  +Rp").append(String.format("%.0f", express)).append("\n");
        for (int i = 0; i < s.addonNama.size(); i++)
            sb.append("- ").append(s.addonNama.get(i))
                    .append("  +Rp").append(String.format("%.0f", s.addonHarga.get(i))).append("\n");
        sb.append("------------------------\n");
        sb.append("Subtotal Layanan : Rp").append(String.format("%.0f", subtotal)).append("\n");
        if (express > 0)    sb.append("Express          : Rp").append(String.format("%.0f", express)).append("\n");
        if (addonTotal > 0) sb.append("Subtotal Add-on  : Rp").append(String.format("%.0f", addonTotal)).append("\n");
        sb.append("TOTAL            : Rp").append(String.format("%.0f", total)).append("\n\n");
        sb.append("Status       : DITERIMA\n");
        sb.append("Est. Selesai : ").append(estimasi).append("\n\n");
        sb.append("Alur: Diterima > Diproses > Selesai > Sudah Diambil\n\n");
        sb.append("Simpan kode ").append(kode).append(" untuk cek status.\n");
        sb.append("Terima kasih sudah menggunakan Washie!");

        reset(s);
        return new BotResponse(sb.toString(), ResponseType.NOTA, kode);
    }

    // =========================================================================
    //  INFO
    // =========================================================================
    private BotResponse handleInfo(String type, String term) {
        Optional<Layanan> opt = cariLayananDb(term);
        if (opt.isEmpty()) return txt("Maaf, informasi layanan " + term + " belum tersedia.");
        Layanan l = opt.get();
        String satuan  = l.isPerKg() ? "/kg" : "/item";
        double expRate = getExpressSurchargeRate(l);
        return switch (type) {
            case "HARGA" -> txt(
                    "Harga layanan " + l.getNamaLayanan() + ":\n\n" +
                            "- Standar : Rp" + String.format("%.0f", l.getHarga()) + satuan + "  (" + l.getEstimasiWaktu() + ")\n" +
                            (l.isBisaExpress()
                                    ? "- Express : Rp" + String.format("%.0f", l.getHarga() + expRate) + satuan + "  (1 Hari)  (+Rp" + String.format("%.0f", expRate) + satuan + ")\n"
                                    : "- Express : tidak tersedia\n") +
                            "\nMau pesan? Ketik: " + l.getNamaLayanan().toLowerCase() + (l.isPerKg() ? " 2 kg" : "")
            );
            case "ESTIMASI" -> txt(
                    "Estimasi waktu " + l.getNamaLayanan() + ":\n\n" +
                            "- Standar : " + l.getEstimasiWaktu() + "\n" +
                            "- Express : " + (l.isBisaExpress() ? "1 Hari Kerja" : "tidak tersedia") + "\n\n" +
                            "Harga: Rp" + String.format("%.0f", l.getHarga()) + satuan + "\n\n" +
                            "Estimasi dihitung sejak pakaian diterima.\n" +
                            "Mau pesan? Ketik: " + l.getNamaLayanan().toLowerCase() + (l.isPerKg() ? " 2 kg" : "")
            );
            case "DESKRIPSI" -> {
                Map<String,String> desk = new HashMap<>(Map.of(
                        "Dry Cleaning",
                        "Pencucian menggunakan cairan kimia khusus, bukan air. Cocok untuk jas, kebaya, " +
                                "gaun pengantin, wol, atau sutra. Aman untuk pakaian yang tidak boleh dicuci air biasa.",
                        "Cuci Kering",
                        "Cuci + pengeringan mesin tanpa setrika. Cocok untuk pakaian kasual sehari-hari " +
                                "yang tidak butuh kerapian lipatan.",
                        "Cuci + Setrika",
                        "Layanan lengkap: cuci, keringkan, lalu setrika hingga rapi. " +
                                "Cocok untuk kemeja, baju kerja, dan pakaian formal.",
                        "Setrika Saja",
                        "Layanan penyetrikaan untuk pakaian yang sudah bersih. " +
                                "Cocok jika pakaian hanya perlu dirapikan tanpa perlu dicuci.",
                        "Cuci Boneka",
                        "Pencucian lembut khusus stuffed toy / boneka berbahan kain. " +
                                "Dikerjakan dengan metode aman agar tidak merusak bentuk. Harga per item.",
                        "Cuci Karpet",
                        "Pencucian karpet menggunakan mesin khusus. " +
                                "Tersedia untuk berbagai jenis dan ketebalan karpet. Harga per meter persegi.",
                        "Cuci Bedcover",
                        "Pencucian bedcover / selimut besar. Dikerjakan dengan mesin berkapasitas besar. " +
                                "Harga per item.",
                        "Cuci Selimut",
                        "Pencucian selimut tebal maupun tipis. " +
                                "Harga per item.",
                        "Cuci Handuk",
                        "Pencucian khusus kain berbahan terry/handuk. " +
                                "Dijaga kelembutannya agar tidak rusak.",
                        "Cuci Gorden & Vitrase",
                        "Pencucian tirai, gorden, dan vitrase. Dikerjakan dengan hati-hati " +
                                "agar tidak merusak bahan tipis. Harga per item."
                ));
                yield txt(l.getNamaLayanan() + "\n\n" +
                        desk.getOrDefault(l.getNamaLayanan(), "Layanan pencucian profesional dari Washie Laundry.") +
                        "\n\nHarga    : Rp" + String.format("%.0f", l.getHarga()) + satuan +
                        "\nEstimasi : " + l.getEstimasiWaktu() +
                        (l.isBisaExpress() ? "\nExpress  : tersedia (1 Hari Kerja)" : "\nExpress  : tidak tersedia") +
                        "\n\nMau pesan? Ketik: " + l.getNamaLayanan().toLowerCase() + (l.isPerKg() ? " 2 kg" : ""));
            }
            default -> fallback();
        };
    }

    private BotResponse tanyaLayanan() {
        List<Layanan> aktif = layananService.getLayananAktif();
        if (aktif.isEmpty()) return txt("Maaf, belum ada layanan aktif. Hubungi kami via WhatsApp.");
        StringBuilder sb = new StringBuilder("Mau laundry apa?\n\n");
        List<Layanan> perKg   = aktif.stream().filter(Layanan::isPerKg).collect(Collectors.toList());
        List<Layanan> perItem = aktif.stream().filter(l -> !l.isPerKg()).collect(Collectors.toList());
        if (!perKg.isEmpty()) {
            sb.append("Per Kilogram:\n");
            for (Layanan l : perKg) sb.append(String.format("- %-28s Rp%.0f/kg\n", l.getNamaLayanan(), l.getHarga()));
        }
        if (!perItem.isEmpty()) {
            sb.append("\nPer Item:\n");
            for (Layanan l : perItem) sb.append(String.format("- %-28s Rp%.0f/item\n", l.getNamaLayanan(), l.getHarga()));
        }
        sb.append("\nKetik nama layanan, misal: cuci setrika 2 kg");
        return txt(sb.toString());
    }

    private BotResponse daftarLayanan() { return tanyaLayanan(); }

    private BotResponse cekStatus(String kode) {
        return pesananService.getByKode(kode).map(p -> {
            String st = switch (p.getStatus()) {
                case DIPROSES -> "Sedang Diproses";
                case SELESAI  -> "Selesai - siap diambil!";
                case DIAMBIL  -> "Sudah Diambil";
            };
            return txt("Status Pesanan " + p.getKodePesanan() + ":\n\n" +
                    "Nama    : " + p.getUser().getNamaLengkap() + "\n" +
                    "Layanan : " + p.getLayanan().getNamaLayanan() + "\n" +
                    "Masuk   : " + p.getTanggalMasuk() + "\n" +
                    "Status  : " + st);
        }).orElse(txt("Pesanan " + kode + " tidak ditemukan.\nPastikan kode benar, contoh: WS-001."));
    }

    private BotResponse jamOps() {
        String sf = infoService.getNilai("JAM_OPERASIONAL","senin_jumat").orElse("08.00-21.00");
        String sm = infoService.getNilai("JAM_OPERASIONAL","sabtu_minggu").orElse("09.00-19.00");
        String hl = infoService.getNilai("JAM_OPERASIONAL","hari_libur").orElse("Tutup");
        return txt("Jam Operasional Washie:\n\nSenin-Jumat  : " + sf + "\nSabtu-Minggu : " + sm + "\nHari Libur   : " + hl);
    }

    private BotResponse lokasi() {
        String al = infoService.getNilai("LOKASI_KONTAK","alamat").orElse("-");
        String wa = infoService.getNilai("LOKASI_KONTAK","whatsapp").orElse("-");
        String ig = infoService.getNilai("LOKASI_KONTAK","instagram").orElse("-");
        return txt("Lokasi & Kontak Washie:\n\nAlamat    : " + al + "\nWhatsApp  : " + wa + "\nInstagram : " + ig);
    }

    private BotResponse salam() {
        StringBuilder sb = new StringBuilder();
        sb.append("Halo! Selamat datang di Washie Laundry.\n\n");

        // Fix #2: tampilkan pengumuman aktif dari admin jika ada
        List<com.washie.model.InfoEntity> pengumuman = infoService.getPengumuman();
        if (!pengumuman.isEmpty()) {
            sb.append("--- Info dari Washie ---\n");
            for (com.washie.model.InfoEntity p : pengumuman) {
                sb.append("[").append(p.getKunci()).append("]\n");
                sb.append(p.getNilai()).append("\n\n");
            }
            sb.append("------------------------\n\n");
        }

        sb.append("Saya bisa membantu:\n");
        sb.append("- Pesan layanan      : ketik misal cuci baju 2 kg\n");
        sb.append("- Cek harga          : ketik misal berapa harga cuci setrika\n");
        sb.append("- Estimasi layanan   : ketik misal berapa lama cuci kering\n");
        sb.append("- Info layanan       : ketik misal apa itu dry cleaning\n");
        sb.append("- Status pesanan     : ketik kode misal cek WS-001\n");
        sb.append("- Jam buka           : ketik jam operasional\n");
        sb.append("- Lokasi             : ketik lokasi\n");
        sb.append("- Pengumuman         : ketik info terbaru\n\n");
        sb.append("Ada yang bisa saya bantu?");
        return txt(sb.toString());
    }

    /** Fix #2: user bisa tanya info/pengumuman kapan saja */
    private BotResponse getPengumuman() {
        List<com.washie.model.InfoEntity> list = infoService.getPengumuman();
        if (list.isEmpty())
            return txt("Saat ini belum ada informasi atau pengumuman dari Washie.\nUntuk info lebih lanjut hubungi kami via WhatsApp.");
        StringBuilder sb = new StringBuilder("Informasi terbaru dari Washie:\n\n");
        for (com.washie.model.InfoEntity p : list) {
            sb.append("[").append(p.getKunci()).append("]\n");
            sb.append(p.getNilai()).append("\n\n");
        }
        return txt(sb.toString().trim());
    }

    private BotResponse fallback() {
        return txt("Maaf, saya belum mengerti maksud Anda.\n\n" +
                "Coba ketik:\n" +
                "- cuci baju 2 kg               : pesan laundry\n" +
                "- berapa harga cuci kering      : cek harga\n" +
                "- berapa lama cuci setrika      : estimasi waktu\n" +
                "- apa itu dry cleaning          : info layanan\n" +
                "- daftar layanan               : lihat semua layanan\n" +
                "- cek WS-001                   : status pesanan\n" +
                "- lokasi                       : alamat & kontak\n" +
                "- jam operasional              : jam buka\n" +
                "- info terbaru                 : pengumuman dari admin");
    }

    // =========================================================================
    //  UTILS
    // =========================================================================
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

    private double extractBerat(String input) {
        Matcher m = BERAT_PAT.matcher(input);
        while (m.find()) {
            try { double v = Double.parseDouble(m.group(1).replace(",",".")); if (v>0&&v<1000) return v; }
            catch (NumberFormatException ignored) {}
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

    private void reset(ChatSession s) {
        s.state=ConvState.IDLE; s.layanan=null; s.berat=0;
        s.kecepatan=null; s.expressHarga=0;
        s.addonNama.clear(); s.addonHarga.clear();
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
}

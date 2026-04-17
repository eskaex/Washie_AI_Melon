package com.washie.engine;

import com.washie.model.InfoEntity;
import com.washie.model.Layanan;
import com.washie.model.Pesanan;
import com.washie.service.InfoService;
import com.washie.service.LayananService;
import com.washie.service.PesananService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class ChatEngine {

    private final LayananService layananService;
    private final PesananService pesananService;
    private final InfoService infoService;

    public ChatEngine(LayananService layananService,
                      PesananService pesananService,
                      InfoService infoService) {
        this.layananService = layananService;
        this.pesananService = pesananService;
        this.infoService = infoService;
    }

    /**
     * Main entry: returns a BotResponse with text and optional structured data.
     */
    public BotResponse process(String input) {
        if (input == null || input.isBlank()) {
            return new BotResponse("Maaf, saya tidak mengerti. Silahkan ketik pertanyaan Anda.", ResponseType.TEXT);
        }

        String lower = input.toLowerCase().trim();

        // Greetings
        if (matchesAny(lower, "halo", "hai", "hi", "hello", "selamat", "hei")) {
            return new BotResponse(
                    "Halo! 👋 Saya Washie, asisten virtual laundry Anda.\n" +
                            "Saya bisa membantu Anda dengan:\n" +
                            "• Cek harga layanan\n" +
                            "• Status pesanan\n" +
                            "• Jam operasional\n" +
                            "• Lokasi laundry\n\n" +
                            "Ada yang bisa saya bantu?",
                    ResponseType.TEXT
            );
        }

        // Layanan / harga
        if (matchesAny(lower, "layanan", "harga", "tarif", "biaya", "cuci", "jenis", "apa saja", "daftar")) {
            return getLayananResponse();
        }

        // Status pesanan
        if (matchesAny(lower, "status", "pesanan", "order", "cek pesanan", "lacak")) {
            // Try to extract order code from input
            String kode = extractOrderCode(input);
            if (kode != null) {
                return getStatusPesananResponse(kode);
            }
            return new BotResponse(
                    "Untuk mengecek status pesanan, mohon masukkan kode pesanan Anda.\n" +
                            "Contoh: WS-001\n\nKode pesanan bisa ditemukan di struk laundry Anda.",
                    ResponseType.TEXT
            );
        }

        // Jam operasional
        if (matchesAny(lower, "jam", "buka", "tutup", "operasional", "waktu", "hari", "minggu")) {
            return getJamOperasionalResponse();
        }

        // Lokasi & kontak
        if (matchesAny(lower, "lokasi", "alamat", "dimana", "di mana", "whatsapp", "wa",
                "kontak", "instagram", "ig", "telepon", "telpon", "hubungi")) {
            return getLokasiResponse();
        }

        // Estimasi waktu
        if (matchesAny(lower, "estimasi", "berapa lama", "kapan", "selesai", "jadi")) {
            return getEstimasiResponse();
        }

        // Terima kasih
        if (matchesAny(lower, "terima kasih", "thanks", "makasih", "thank you", "thx")) {
            return new BotResponse(
                    "Sama-sama! 😊 Senang bisa membantu Anda.\n" +
                            "Jika ada pertanyaan lain, jangan ragu untuk bertanya ya!",
                    ResponseType.TEXT
            );
        }

        // Fallback
        return new BotResponse(
                "Maaf, saya belum bisa menjawab pertanyaan tersebut. 🤔\n\n" +
                        "Anda bisa tanya tentang:\n" +
                        "• Harga & layanan cuci\n" +
                        "• Status pesanan (misal: cek WS-001)\n" +
                        "• Jam operasional\n" +
                        "• Lokasi & kontak\n\n" +
                        "Atau hubungi kami langsung via WhatsApp.",
                ResponseType.TEXT
        );
    }

    private BotResponse getLayananResponse() {
        List<Layanan> list = layananService.getLayananAktif();
        if (list.isEmpty()) {
            return new BotResponse("Saat ini belum ada layanan yang tersedia. Silahkan hubungi kami.", ResponseType.TEXT);
        }
        StringBuilder sb = new StringBuilder("Berikut daftar layanan laundry Washie:\n\n");
        for (Layanan l : list) {
            sb.append(String.format("• %s\n  Rp%.0f/kg | %s\n\n",
                    l.getNamaLayanan(), l.getHarga(), l.getEstimasiWaktu()));
        }
        sb.append("Ada pertanyaan lain tentang layanan kami?");
        return new BotResponse(sb.toString(), ResponseType.LAYANAN, list);
    }

    private BotResponse getStatusPesananResponse(String kode) {
        Optional<Pesanan> opt = pesananService.getByKode(kode);
        if (opt.isEmpty()) {
            return new BotResponse(
                    "Pesanan dengan kode \"" + kode + "\" tidak ditemukan. 😕\n" +
                            "Pastikan kode pesanan Anda benar (contoh: WS-001).\n" +
                            "Jika masih bermasalah, hubungi kami via WhatsApp.",
                    ResponseType.TEXT
            );
        }
        Pesanan p = opt.get();
        String statusEmoji = switch (p.getStatus()) {
            case DIPROSES -> "🔄 Sedang Diproses";
            case SELESAI -> "✅ Selesai";
            case DIAMBIL -> "📦 Sudah Diambil";
        };
        String msg = String.format(
                "Status pesanan %s:\n\n" +
                        "👤 Nama: %s\n" +
                        "🧺 Layanan: %s\n" +
                        "📅 Tanggal Masuk: %s\n" +
                        "📌 Status: %s",
                p.getKodePesanan(),
                p.getUser().getNamaLengkap(),
                p.getLayanan().getNamaLayanan(),
                p.getTanggalMasuk(),
                statusEmoji
        );
        return new BotResponse(msg, ResponseType.TEXT);
    }

    private BotResponse getJamOperasionalResponse() {
        String seninjumat = infoService.getNilai("JAM_OPERASIONAL", "senin_jumat").orElse("08.00 - 21.00");
        String sabminggu  = infoService.getNilai("JAM_OPERASIONAL", "sabtu_minggu").orElse("09.00 - 19.00");
        String libur      = infoService.getNilai("JAM_OPERASIONAL", "hari_libur").orElse("Tutup");
        String msg = String.format(
                "⏰ Jam Operasional Washie Laundry:\n\n" +
                        "📅 Senin - Jumat: %s\n" +
                        "📅 Sabtu - Minggu: %s\n" +
                        "🚫 Hari Libur Nasional: %s\n\n" +
                        "Kami siap melayani Anda!",
                seninjumat, sabminggu, libur
        );
        return new BotResponse(msg, ResponseType.TEXT);
    }

    private BotResponse getLokasiResponse() {
        String alamat   = infoService.getNilai("LOKASI_KONTAK", "alamat").orElse("Belum tersedia");
        String wa       = infoService.getNilai("LOKASI_KONTAK", "whatsapp").orElse("-");
        String ig       = infoService.getNilai("LOKASI_KONTAK", "instagram").orElse("-");
        String msg = String.format(
                "📍 Lokasi & Kontak Washie Laundry:\n\n" +
                        "🏠 Alamat: %s\n" +
                        "📱 WhatsApp: %s\n" +
                        "📸 Instagram: %s\n\n" +
                        "Jangan ragu untuk menghubungi kami!",
                alamat, wa, ig
        );
        return new BotResponse(msg, ResponseType.TEXT);
    }

    private BotResponse getEstimasiResponse() {
        List<Layanan> list = layananService.getLayananAktif();
        if (list.isEmpty()) {
            return new BotResponse("Estimasi waktu belum tersedia saat ini.", ResponseType.TEXT);
        }
        StringBuilder sb = new StringBuilder("⏳ Estimasi waktu pengerjaan:\n\n");
        for (Layanan l : list) {
            sb.append(String.format("• %s: %s\n", l.getNamaLayanan(), l.getEstimasiWaktu()));
        }
        sb.append("\nWaktu dihitung mulai dari hari masuk pakaian.");
        return new BotResponse(sb.toString(), ResponseType.TEXT);
    }

    private String extractOrderCode(String input) {
        // Match pattern WS-XXX (case insensitive)
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("\\b(WS-\\d+)\\b", java.util.regex.Pattern.CASE_INSENSITIVE)
                .matcher(input);
        if (m.find()) return m.group(1).toUpperCase();
        return null;
    }

    private boolean matchesAny(String input, String... keywords) {
        for (String kw : keywords) {
            if (input.contains(kw)) return true;
        }
        return false;
    }

    // ===== Inner classes =====

    public enum ResponseType {
        TEXT, LAYANAN, PESANAN
    }

    public static class BotResponse {
        private final String text;
        private final ResponseType type;
        private final Object data;

        public BotResponse(String text, ResponseType type) {
            this.text = text;
            this.type = type;
            this.data = null;
        }

        public BotResponse(String text, ResponseType type, Object data) {
            this.text = text;
            this.type = type;
            this.data = data;
        }

        public String getText() { return text; }
        public ResponseType getType() { return type; }
        public Object getData() { return data; }
    }
}

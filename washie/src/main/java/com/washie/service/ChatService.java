import java.util.regex.Pattern;

public class ChatService {
    private InfoService infoService;
    private LayananService layananService;

    public ChatService() {
        this.infoService = new InfoService();
        this.layananService = new LayananService();
    }

    public String prosesPesan(String inputTeks) {
        if (inputTeks == null || inputTeks.trim().isEmpty()) {
            return "Pesan kamu kosong. Silakan ketik pertanyaanmu ya!";
        }

        String teks = inputTeks.toLowerCase();

        if (cocok(teks, "jam|buka|tutup|operasional"))             return infoService.getJamOperasional();
        if (cocok(teks, "lokasi|alamat|dimana|di mana"))           return infoService.getLokasi();
        if (cocok(teks, "kontak|telepon|wa|whatsapp|hubungi|nomor")) return infoService.getKontak();

        if (cocok(teks, "harga|tarif|biaya|berapa"))              return layananService.getDaftarHarga();
        if (cocok(teks, "layanan|jenis|apa saja|fasilitas"))      return layananService.getDaftarLayanan();
        if (cocok(teks, "pakaian|baju|celana|kaos|kemeja"))       return layananService.getInfoLayanan("Pakaian");
        if (cocok(teks, "seprai|sprei|bed cover|selimut"))        return layananService.getInfoLayanan("Seprai");
        if (cocok(teks, "handuk"))                                return layananService.getInfoLayanan("Handuk");
        if (cocok(teks, "jaket|hoodie|sweater"))                  return layananService.getInfoLayanan("Jaket");
        if (cocok(teks, "boneka|stuffed|plush"))                  return layananService.getInfoLayanan("Boneka");

        // --- Fallback ---
        return "Maaf, Washie belum mengerti pertanyaanmu.\n"
                + "Kamu bisa tanya tentang:\n"
                + "  • Layanan (pakaian, seprai, handuk, jaket, boneka)\n"
                + "  • Harga / tarif\n"
                + "  • Jam operasional\n"
                + "  • Lokasi\n"
                + "  • Kontak";
    }

    /** Mengecek apakah teks cocok dengan pola Regex. */
    private boolean cocok(String teks, String pola) {
        return Pattern.compile(pola).matcher(teks).find();
    }
}
import java.util.LinkedHashMap;
import java.util.Map;

public class LayananService {

    // Data layanan: nama -> {harga, satuan, estimasi}
    private Map<String, String[]> dataLayanan;

    public LayananService() {
        dataLayanan = new LinkedHashMap<>();
        dataLayanan.put("Pakaian",  new String[]{"6000",  "kg",    "1-2 hari"});
        dataLayanan.put("Seprai",   new String[]{"10000", "lembar","2-3 hari"});
        dataLayanan.put("Handuk",   new String[]{"5000",  "lembar","1-2 hari"});
        dataLayanan.put("Jaket",    new String[]{"15000", "pcs",   "2-3 hari"});
        dataLayanan.put("Boneka",   new String[]{"20000", "pcs",   "3-4 hari"});
    }

    public String getDaftarLayanan() {
        StringBuilder sb = new StringBuilder("Daftar Layanan Washie:\n");
        for (Map.Entry<String, String[]> e : dataLayanan.entrySet()) {
            String[] d = e.getValue();
            sb.append(String.format("  • %-10s : Rp %s/%s | estimasi %s%n",
                    e.getKey(), d[0], d[1], d[2]));
        }
        return sb.toString();
    }

    public String getDaftarHarga() {
        StringBuilder sb = new StringBuilder("Daftar Harga Washie:\n");
        for (Map.Entry<String, String[]> e : dataLayanan.entrySet()) {
            String[] d = e.getValue();
            sb.append(String.format("  • %-10s : Rp %s/%s%n",
                    e.getKey(), d[0], d[1]));
        }
        return sb.toString();
    }

    public String getInfoLayanan(String nama) {
        String[] d = dataLayanan.get(nama);
        if (d == null) return "Layanan \"" + nama + "\" tidak ditemukan.";
        return String.format("Layanan %s:%n  • Harga    : Rp %s/%s%n  • Estimasi : %s",
                nama, d[0], d[1], d[2]);
    }
}
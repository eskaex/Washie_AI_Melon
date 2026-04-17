public class InfoService {

    // Data info dasar laundry — ganti sesuai data nyata
    private String jamOperasional = "Senin - Sabtu: 07.00 - 21.00 WIB\nMinggu: 08.00 - 18.00 WIB";
    private String lokasi         = "Jl. Colombo No. 10, Caturtunggal, Sleman, Yogyakarta";
    private String kontak         = "WhatsApp / Telepon: 0812-3456-7890";

    public String getJamOperasional() {
        return "Jam Operasional Washie Laundry:\n" + jamOperasional;
    }

    public String getLokasi() {
        return "Lokasi Washie Laundry:\n" + lokasi;
    }

    public String getKontak() {
        return "Kontak Washie Laundry:\n" + kontak;
    }
}
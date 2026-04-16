package com.washie.engine;

public class ChatEngine {

    public String process(String input) {
        if (input == null || input.isBlank()) {
            return "Maaf, saya tidak mengerti. Silahkan ketik pertanyaan Anda.";
        }

        String lower = input.toLowerCase().trim();

        if (matchesAny(lower, "halo", "hai", "hi", "hello", "selamat")) {
            return "Halo! 👋 Saya Washie, asisten virtual laundry Anda. Ada yang bisa saya bantu?";
        }

        if (matchesAny(lower, "layanan", "harga", "tarif", "biaya")) {
            return "Kami memiliki layanan cuci kiloan, cuci setrika, dan dry cleaning. Silahkan hubungi kami untuk info harga.";
        }

        if (matchesAny(lower, "jam", "buka", "tutup", "operasional")) {
            return "⏰ Jam operasional kami:\nSenin-Jumat: 08.00 - 21.00\nSabtu-Minggu: 09.00 - 19.00";
        }

        if (matchesAny(lower, "lokasi", "alamat", "dimana")) {
            return "📍 Kami berlokasi di Jl. Contoh No. 123. Hubungi WhatsApp kami untuk info lebih lanjut.";
        }

        if (matchesAny(lower, "terima kasih", "makasih", "thanks")) {
            return "Sama-sama! 😊 Jika ada pertanyaan lain, jangan ragu untuk bertanya ya!";
        }

        // FALLBACK
        return "Maaf, saya belum bisa menjawab pertanyaan tersebut. 🤔\n\n" +
                "Anda bisa tanya tentang:\n" +
                "• Harga & layanan cuci\n" +
                "• Jam operasional\n" +
                "• Lokasi & kontak\n\n" +
                "Atau hubungi kami langsung via WhatsApp.";
    }

    private boolean matchesAny(String input, String... keywords) {
        for (String kw : keywords) {
            if (input.contains(kw)) return true;
        }
        return false;
    }
}
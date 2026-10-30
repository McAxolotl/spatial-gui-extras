package mcaxolotl.spatialguiextras;

import com.google.gson.JsonParser;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public final class LocalizationTests {
    private static com.google.gson.JsonObject read(String code) throws Exception {
        try (var input =
                LocalizationTests.class.getResourceAsStream(
                        "/assets/spatial_gui_extras/lang/" + code + ".json")) {
            if (input == null) throw new AssertionError("missing locale " + code);
            try (var reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
                return JsonParser.parseReader(reader).getAsJsonObject();
            }
        }
    }

    public static void main(String[] args) throws Exception {
        var english = read("en_us");
        int count = 0;
        for (String code :
                "ar_ar de_de en_us es_es fr_fr he_il hu_hu id_id ko_kr pl_pl pt_br pt_pt ru_ru th_th tr_tr uk_ua ur_pk vi_vn zh_cn zh_tw ar_sa"
                        .split(" ")) {
            var translated = read(code);
            if (!translated.keySet().equals(english.keySet()))
                throw new AssertionError("locale key mismatch " + code);
            for (var entry : translated.entrySet())
                if (!entry.getValue().isJsonPrimitive() || entry.getValue().getAsString().isBlank())
                    throw new AssertionError("blank translation " + code + " " + entry.getKey());
            if (!code.equals("en_us") && translated.equals(english))
                throw new AssertionError("locale only copies English " + code);
            count++;
        }
        System.out.println(
                "PASS locale coverage="
                        + count
                        + " including Arabic alias; keys="
                        + english.size());
    }
}

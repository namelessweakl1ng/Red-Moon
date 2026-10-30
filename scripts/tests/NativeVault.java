package com.redmoon.vault;

/** Real host-JVM JNI smoke test; Android OS behavior is tested separately. */
public final class NativeVault {
    static { System.load(System.getProperty("redmoon.native")); }
    public static native String initialize(String path);
    public static native String request(String json, long elapsedSeconds);
    private static void require(boolean condition) {
        if (!condition) throw new AssertionError("JNI contract failure (secret response omitted)");
    }
    private static String ok(String json, long time) {
        String result = request(json, time);
        require(!result.contains("\"error\""));
        return result;
    }
    public static void main(String[] args) throws Exception {
        java.nio.file.Path vault = java.nio.file.Path.of(args[0], "vault.rm");
        require(!initialize(vault.toString()).contains("\"error\""));
        ok("{\"action\":\"create\",\"password\":\"long synthetic café passphrase\"}", 0);
        ok("{\"action\":\"save\",\"entry\":{\"id\":\"\",\"title\":\"Café\",\"username\":\"alice\",\"password\":\"Synthetic_JNI_secret_2026\",\"notes\":\"private JNI note\",\"url\":\"\",\"category\":\"\",\"tags\":[],\"favorite\":false,\"created_at\":0,\"modified_at\":0}}", 1);
        String list = ok("{\"action\":\"list\"}", 2);
        require(list.contains("Café") && !list.contains("Synthetic_JNI_secret_2026") && !list.contains("private JNI note"));
        java.util.regex.Matcher id = java.util.regex.Pattern.compile("\"id\":\"([^\"]+)\"").matcher(list);
        require(id.find());
        require(ok("{\"action\":\"reveal\",\"id\":\"" + id.group(1) + "\"}", 3).contains("Synthetic_JNI_secret_2026"));
        ok("{\"action\":\"lock\"}", 4);
        require(request("{\"action\":\"list\"}", 5).contains("\"error\""));
        require(request("{\"action\":\"unlock\",\"password\":\"wrong\"}", 6).contains("\"error\""));
        ok("{\"action\":\"unlock\",\"password\":\"long synthetic café passphrase\"}", 7);
        for (String kind : new String[]{"login","secure_note","identity","indian_document","financial","custom"}) {
            ok("{\"action\":\"save_record\",\"record\":{\"id\":\"\",\"kind\":\"" + kind + "\",\"title\":\"synthetic-" + kind + "\",\"category\":\"personal\",\"tags\":[],\"favorite\":false,\"created_at\":0,\"modified_at\":0,\"fields\":[{\"id\":\"private\",\"label\":\"Private value\",\"field_type\":\"secret\",\"value\":\"JNI_new_secret_value\"}],\"notes\":\"JNI private note body\",\"checklist\":[]}}", 8);
        }
        String summaries = ok("{\"action\":\"list_records\"}", 8);
        require(summaries.contains("synthetic-custom") && !summaries.contains("JNI_new_secret_value") && !summaries.contains("JNI private note body"));
        require(ok("{\"action\":\"search_records\",\"query\":\"private note body\"}", 8).contains("synthetic-secure_note"));
        String backup = ok("{\"action\":\"backup\"}", 8);
        java.util.regex.Matcher data = java.util.regex.Pattern.compile("\"data\":\"([^\"]+)\"").matcher(backup);
        require(data.find());
        require(ok("{\"action\":\"restore\",\"data\":\"" + data.group(1) + "\",\"password\":\"long synthetic café passphrase\",\"current\":\"long synthetic café passphrase\"}", 9).contains("\"record_count\":7"));
        require(!new String(java.nio.file.Files.readAllBytes(vault), java.nio.charset.StandardCharsets.ISO_8859_1).contains("Synthetic_JNI_secret_2026"));
        System.out.println("Real JNI create/save/list/reveal/lock/unlock/backup/restore smoke passed.");
    }
}

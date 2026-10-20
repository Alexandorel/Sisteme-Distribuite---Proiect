package ro.ubm.sd.common;

import java.util.HashMap;
import java.util.Map;

/** Parser minimal pentru argumente de forma --cheie valoare si flag-uri --cheie. */
public final class Args {

    private final Map<String, String> values = new HashMap<>();

    public Args(String[] argv) {
        for (int i = 0; i < argv.length; i++) {
            if (!argv[i].startsWith("--")) {
                continue;
            }
            String key = argv[i].substring(2);
            // valoare daca urmeaza un token care nu e alta optiune, altfel flag
            if (i + 1 < argv.length && !argv[i + 1].startsWith("--")) {
                values.put(key, argv[++i]);
            } else {
                values.put(key, "");
            }
        }
    }

    /** Valoarea optiunii, sau null daca lipseste. */
    public String get(String key) {
        return values.get(key);
    }

    /** Valoarea optiunii, sau defaultValue daca lipseste. */
    public String get(String key, String defaultValue) {
        String v = values.get(key);
        return v == null ? defaultValue : v;
    }

    /** true daca optiunea a fost data (ca flag sau cu valoare). */
    public boolean has(String key) {
        return values.containsKey(key);
    }

    /** Valoarea obligatorie; eroare clara daca lipseste. */
    public String require(String key) {
        String v = values.get(key);
        if (v == null || v.isEmpty()) {
            throw new IllegalArgumentException("Lipseste argumentul obligatoriu: --" + key);
        }
        return v;
    }
}

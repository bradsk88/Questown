package ca.bradj.questown.core;

import com.electronwill.nightconfig.core.Config;

import java.util.LinkedHashMap;
import java.util.Map;

// The item-weights table as one "key=value;key=value" string. A map value does not survive a TOML
// load and save, so the config would rewrite itself forever.
public final class ItemWeightCodec {
    private ItemWeightCodec() {
    }

    public static String encode(Config weights) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Object> e : weights.valueMap().entrySet()) {
            if (sb.length() > 0) {
                sb.append(';');
            }
            sb.append(e.getKey()).append('=').append(((Number) e.getValue()).intValue());
        }
        return sb.toString();
    }

    public static Map<String, Integer> decode(String s) {
        Map<String, Integer> map = new LinkedHashMap<>();
        if (s == null || s.isEmpty()) {
            return map;
        }
        for (String pair : s.split(";")) {
            int eq = pair.indexOf('=');
            if (eq < 0) {
                continue;
            }
            try {
                map.put(pair.substring(0, eq), Integer.parseInt(pair.substring(eq + 1).trim()));
            } catch (NumberFormatException ignored) {
                // Skip a malformed pair, keep the rest.
            }
        }
        return map;
    }
}

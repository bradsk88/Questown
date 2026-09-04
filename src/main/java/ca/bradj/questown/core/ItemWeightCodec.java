package ca.bradj.questown.core;

import com.electronwill.nightconfig.core.Config;

import java.util.LinkedHashMap;
import java.util.Map;

// Pure (Forge-free) encode/decode of the item-weights table.
//
// ForgeConfigSpec cannot round-trip a nightconfig Config value through TOML: a TOML load
// yields a SimpleCommentedConfig while the default is a SimpleConfig, so every load is
// flagged "not correct" and re-saved (rewrite, then the file watcher reloads it, forever).
// Encoding the weights as a single string makes them round-trip cleanly.
//
// Format: "key=value;key=value;...". Keys (e.g. minecraft:crafting_table, #minecraft:beds)
// never contain '=' or ';', so both are safe delimiters. Values are ints.
//
// Kept separate from RecipeItemConfig so this round-trip is unit-testable without a Forge
// registry bootstrap.
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
                // skip one malformed pair rather than dropping the whole set
            }
        }
        return map;
    }
}

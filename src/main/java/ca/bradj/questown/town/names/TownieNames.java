package ca.bradj.questown.town.names;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;


import ca.bradj.questown.mc.Compat;

/**
 * Names villagers after vanilla items: a word from the item's id ("diamond", "shovel"), with
 * materials, forms, qualifiers and a blocklist filtered out. The pool is checked in as a golden
 * file, so a change to the filters shows up in review.
 */
public final class TownieNames {

    private TownieNames() {}

    public static final String DEFAULT_NAMESPACE = "minecraft";

    /** Material words: a condition, not a thing. */
    static final Set<String> MATERIALS = set(
            "waxed", "exposed", "weathered", "oxidized", "cut",
            "polished", "chiseled", "smooth", "cracked", "mossy",
            "raw", "cooked", "damaged"
    );

    /** Forms: shapes many items share. */
    static final Set<String> FORMS = set(
            "block", "item", "slab", "stairs", "wall", "fence", "gate",
            "door", "trapdoor", "button", "plate", "pane", "bars",
            "brick", "bricks", "tile", "tiles", "ingot", "nugget",
            "dust", "shard", "powder", "ore", "log", "planks",
            "sapling", "seeds", "bottle", "bucket"
    );

    /** Sizes and positions. */
    static final Set<String> QUALIFIERS = set(
            "lesser", "greater", "small", "large", "tiny", "huge",
            "upper", "lower", "dead", "infested"
    );

    /** Names that pass the filters and still read badly. */
    static final Set<String> BLOCKLIST = set(
            "rotten", "poisonous", "suspicious"
    );

    static final int MIN_LEN = 3;
    static final int MAX_LEN = 10;

    private static Set<String> set(String... tokens) {
        Set<String> out = new LinkedHashSet<>();
        for (String t : tokens) {
            out.add(t);
        }
        return out;
    }

    /** A name from an id's path, or null when no word passes. */
    public static String nameFromPath(String path) {
        if (path == null) {
            return null;
        }
        String[] tokens = path.split("_");
        for (String token : tokens) {
            if (isStoplisted(token)) {
                continue;
            }
            if (hasDigit(token)) {
                continue;
            }
            if (!inLengthRange(token.length())) {
                continue;
            }
            String name = titleCase(token);
            if (BLOCKLIST.contains(token)) {
                return null;
            }
            return name;
        }
        return null;
    }

    /** The name for an item, or null when its namespace isn't allowed or no word passes. */
    public static String nameForItem(ResourceLocation id) {
        if (id == null || !DEFAULT_NAMESPACE.equals(id.getNamespace())) {
            return null;
        }
        return nameFromPath(id.getPath());
    }

    /** Every registered item's name, by path. */
    public static Map<String, String> generatePool() {
        Map<String, String> pool = new LinkedHashMap<>();
        for (Item item : Compat.itemRegistryStream().toList()) {
            ResourceLocation id = Compat.getItemId(item);
            if (id == null || !DEFAULT_NAMESPACE.equals(id.getNamespace())) {
                continue;
            }
            String name = nameFromPath(id.getPath());
            if (name != null) {
                pool.put(id.getPath(), name);
            }
        }
        return pool;
    }

    static boolean isStoplisted(String token) {
        return MATERIALS.contains(token) || FORMS.contains(token) || QUALIFIERS.contains(token);
    }

    static boolean hasDigit(String token) {
        for (int i = 0; i < token.length(); i++) {
            if (Character.isDigit(token.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    static boolean inLengthRange(int len) {
        return len >= MIN_LEN && len <= MAX_LEN;
    }

    static String titleCase(String token) {
        String lower = token.toLowerCase(Locale.ROOT);
        if (lower.isEmpty()) {
            return "";
        }
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }
}

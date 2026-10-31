package ca.bradj.questown.jobs.declarative;

import ca.bradj.questown.jobs.GathererJournalTest;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves the {@code #ns:name} tag keys that job tool/ingredient declarations may carry
 * into concrete item ids, reading the same data files production uses:
 *
 * <ul>
 *   <li>{@code #questown:...} -&gt; classpath {@code data/questown/tags/items/<name>.json}
 *       (the mod's own tag files, on the test classpath)</li>
 *   <li>{@code #minecraft:...} -&gt; classpath {@code data/minecraft/tags/item/<name>.json}
 *       (vanilla tag data vendored under {@code src/test/resources} - only {@code coals} is
 *       vendored as of 2026-10-09; add the file when a test needs another)</li>
 * </ul>
 *
 * Nested {@code #...} entries inside a tag file are expanded recursively (object-valued
 * entries, e.g. NeoForge's weighted form {@code {"id": "#forge:mushrooms", "required":
 * false}}, are read by their {@code id} field). A tag that cannot be resolved throws
 * {@link IllegalStateException}: a gate left unsatisfiable by an unresolvable tag makes
 * both sides of an equivalence simulation stall identically, and "equivalence" then
 * passes vacuously (the hollow-equivalence trap, 2026-10-09).
 * <p>
 * {@link #matches} serves the *gate* side (a job's tool/ingredient key) and can
 * meet test inventories whose items are either literal item ids or tag references
 * ({@code new TestItem("#minecraft:coals")} stands for "some member of that tag" — the
 * "tag-as-item" convention the pre-existing declarative tests use). Rules:
 * <ul>
 *   <li>literal gate matches the literal item id (fast path, no tag loading)</li>
 *   <li>tag gate matches a literal item iff the item is a member of the gate's tag</li>
 *   <li>tag gate matches a tag item iff the two tags are equal or their memberships intersect</li>
 *   <li>literal gate matches a tag item iff the literal is a member of the item's tag</li>
 * </ul>
 */
public final class TestTagResolver {

    private static final ConcurrentHashMap<String, Set<String>> MEMBERS_CACHE = new ConcurrentHashMap<>();

    private TestTagResolver() {
    }

    /**
     * True if the item satisfies the gate key — see the class javadoc for the four rules
     * (literal vs literal, tag gate vs literal item, tag gate vs tag item, literal gate
     * vs tag item). An unresolvable tag on the gate side throws.
     */
    public static boolean matches(GathererJournalTest.TestItem item, String itemOrTagKey) {
        String itemValue = item.value;
        if (item.isEmpty() || itemValue.isEmpty()) {
            return false; // an empty slot matches nothing, whatever the gate
        }
        if (!itemOrTagKey.startsWith("#")) {
            if (itemOrTagKey.equals(itemValue)) {
                return true;
            }
            return itemValue.startsWith("#") && members(itemValue).contains(itemOrTagKey);
        }
        if (itemOrTagKey.equals(itemValue)) {
            return true; // "tag-as-item" convention: the inventory carries this very tag's items
        }
        Set<String> gate = members(itemOrTagKey);
        if (itemValue.startsWith("#")) {
            for (String member : members(itemValue)) {
                if (gate.contains(member)) {
                    return true;
                }
            }
            return false;
        }
        return gate.contains(itemValue);
    }

    /** The concrete item ids a tag key names. Throws if the tag cannot be resolved. */
    public static Set<String> members(String tagKey) {
        if (!tagKey.startsWith("#")) {
            throw new IllegalArgumentException("not a tag key: " + tagKey);
        }
        return MEMBERS_CACHE.computeIfAbsent(tagKey, k -> expand(k, new java.util.HashSet<>()));
    }

    private static Set<String> expand(String tagKey, Set<String> inProgress) {
        if (!inProgress.add(tagKey)) {
            throw new IllegalStateException("circular tag reference at " + tagKey);
        }
        Set<String> out = new java.util.HashSet<>();
        for (String value : loadValues(tagKey)) {
            if (value.startsWith("#")) {
                out.addAll(expand(value, inProgress));
            } else {
                out.add(value);
            }
        }
        return out;
    }

    private static List<String> loadValues(String tagKey) {
        String body = tagKey.substring(1);
        int nsSep = body.indexOf(':');
        String namespace = body.substring(0, nsSep);
        String name = body.substring(nsSep + 1);
        // vanilla tag data lives under tags/item (singular); modpack-style data under tags/items
        String path = "data/" + namespace + "/tags/" + ("minecraft".equals(namespace) ? "item" : "items")
                + "/" + name + ".json";
        try (InputStream is = TestTagResolver.class.getClassLoader().getResourceAsStream(path)) {
            if (is == null) {
                throw new IllegalStateException(
                        "TestTagResolver: cannot resolve tag " + tagKey
                                + " - no " + path + " on the test classpath"
                                + ("minecraft".equals(namespace)
                                        ? "; vendor the vanilla tag file under src/test/resources"
                                        : "; the mod tag file is missing"));
            }
            JsonObject obj = new Gson().fromJson(new InputStreamReader(is, StandardCharsets.UTF_8), JsonObject.class);
            JsonElement values = obj.has("values") ? obj.get("values") : obj.get("items");
            if (values == null) {
                throw new IllegalStateException("TestTagResolver: no values/items array in " + path);
            }
            List<String> result = new ArrayList<>();
            for (JsonElement e : values.getAsJsonArray()) {
                if (e.isJsonPrimitive()) {
                    result.add(e.getAsString());
                } else if (e.isJsonObject() && e.getAsJsonObject().has("id")) {
                    // weighted/optional entry form (NeoForge); optional entries may be absent
                    JsonObject entry = e.getAsJsonObject();
                    if (entry.has("required") && !entry.get("required").getAsBoolean()) {
                        continue;
                    }
                    result.add(entry.get("id").getAsString());
                }
            }
            return result;
        } catch (IOException e) {
            throw new IllegalStateException("TestTagResolver: cannot read " + path, e);
        }
    }
}

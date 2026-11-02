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
 * Resolves {@code #ns:name} tags from the data files production uses: the mod's own tags, and
 * vendored vanilla tags under {@code src/test/resources}. Nested tags expand. An unknown tag throws,
 * so an unsatisfiable gate can't let an equivalence test pass with both sides stalled.
 *
 * <p>Test items can be item ids or tags ({@code new TestItem("#minecraft:coals")} means some member).
 * A gate matches an item when the ids are equal, one is a member of the other's tag, or two tags share
 * a member.
 */
public final class TestTagResolver {

    private static final ConcurrentHashMap<String, Set<String>> MEMBERS_CACHE = new ConcurrentHashMap<>();

    private TestTagResolver() {
    }

    /** Whether the item satisfies the gate key, by the rules above. */
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
                    // The weighted form; optional entries may be missing.
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

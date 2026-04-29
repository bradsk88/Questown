package ca.bradj.questown.mobs.helperchicken;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Cross-cutting invariants on the helper-chicken hint copy and its alignment
 * with the bubble icons + plain-text fallback. Caught one recurring class of
 * bug: hint copy that contradicts the bubble (e.g. "dislikes the hole in the
 * wall" appearing while the bubble is asking the player to fetch a cobblestone
 * block). The voice rule from {@code docs/onboarding-chicken/REQUIREMENTS.md}
 * §10 — "keep hints from telegraphing mechanics the chicken hasn't visually
 * demonstrated yet" — is enforced indirectly by checking that the no-item
 * hint names the same noun as the bubble's lone icon.
 *
 * <p>Mod-registered icons (TOWN_WAND, WELCOME_MAT_BLOCK, COBBLESTONE_TOWN_FLAG,
 * WORLDLY_SEEDS) are unreachable from the vanilla {@code Bootstrap.bootStrap()}
 * path — those bubble lookups NPE on {@code RegistryObject.get()}. Per
 * CLAUDE.md, those gaps are surfaced via a separate failing assertion in
 * {@link #todo_modRegisteredBubbleStatesAreCoveredByThisSuite()} rather than
 * silently skipped.
 */
class ChickenArcHintsTest {

    private static final Path LANG_PATH = Path.of(
            "src/main/resources/assets/questown/lang/en_us.json"
    );

    private static final Set<ChickenBeatState> TERMINAL = EnumSet.of(
            ChickenBeatState.COMPLETE,
            ChickenBeatState.FORFEIT
    );

    /**
     * States whose no-item bubble icon is a mod-registered Forge item. Bubble
     * lookups for these NPE without full mod loading, so they're skipped by
     * the icon-mention invariant. {@link #todo_modRegisteredBubbleStatesAreCoveredByThisSuite()}
     * will fail until that gap closes.
     */
    private static final Set<ChickenBeatState> MOD_ICON_STATES = EnumSet.of(
            ChickenBeatState.WAITING_FOR_WAND_ON_CAMPFIRE,
            ChickenBeatState.WAITING_FOR_WAND_ON_DOOR,
            ChickenBeatState.WAITING_FOR_PRESSURE_PLATE,
            ChickenBeatState.WAITING_FOR_FLAG_UI,
            ChickenBeatState.AWAITING_WORLDLY_SEEDS_DELIVERY
    );

    private static Map<String, String> lang;
    private static Map<Item, List<String>> tokensByItem;

    @BeforeAll
    static void bootstrap() throws IOException {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        Type t = new TypeToken<Map<String, String>>() {}.getType();
        lang = new Gson().fromJson(Files.readString(LANG_PATH), t);
        tokensByItem = buildTokenMap();
    }

    @Test
    void everyBeat_hasResolvableHintAndPlainKeys() {
        List<String> missing = new ArrayList<>();
        for (ChickenBeatState state : ChickenBeatState.values()) {
            if (TERMINAL.contains(state)) {
                continue;
            }
            for (boolean hasItem : new boolean[]{false, true}) {
                String hk = ChickenArcController.hintKey(state, hasItem, false, false);
                if (hk == null || !lang.containsKey(hk)) {
                    missing.add("hint: state=" + state + " hasItem=" + hasItem + " key=" + hk);
                }
            }
            String pk = ChickenArcController.plainTextKey(state, false, false);
            if (pk == null || !lang.containsKey(pk)) {
                missing.add("plain: state=" + state + " key=" + pk);
            }
        }
        Assertions.assertTrue(missing.isEmpty(),
                "lang keys missing or unresolved: " + missing);
    }

    @Test
    void noItemHint_mentionsBubbleIconNoun() {
        // For every beat whose no-item bubble shows a vanilla item, the
        // no-item hint must contain a noun derived from that item. Catches
        // hint/bubble voice mismatches like "It seems to dislike the hole in
        // the wall" appearing while the bubble shows cobblestone.
        //
        // SUNSET_AND_MAP is exempt: its phase-1 chest icon foreshadows what
        // the chicken will drop, not what the player should fetch — the hint
        // intentionally talks about the chicken being "up to something".
        List<String> failures = new ArrayList<>();
        for (ChickenBeatState state : ChickenBeatState.values()) {
            if (TERMINAL.contains(state)
                    || MOD_ICON_STATES.contains(state)
                    || state == ChickenBeatState.SUNSET_AND_MAP) {
                continue;
            }
            ChickenArcBubbles.Bubble bubble = ChickenArcBubbles.forState(state, false, false, false);
            ItemStack icon = bubble.iconA();
            if (icon.isEmpty()) {
                // Texture-mode bubble (sunset) — no icon noun to check.
                continue;
            }
            String hintKey = ChickenArcController.hintKey(state, false, false, false);
            String hint = lang.get(hintKey);
            List<String> tokens = tokensByItem.getOrDefault(icon.getItem(), List.of());
            if (tokens.isEmpty()) {
                failures.add(state + ": no token mapping for " + icon.getItem()
                        + " (extend buildTokenMap())");
                continue;
            }
            String lower = hint == null ? "" : hint.toLowerCase(Locale.ROOT);
            boolean hit = tokens.stream().anyMatch(lower::contains);
            if (!hit) {
                failures.add(state + ": hint \"" + hint + "\" should mention one of "
                        + tokens + " (icon=" + icon.getItem() + ")");
            }
        }
        Assertions.assertTrue(failures.isEmpty(),
                String.join("\n  ", prepend("hint/bubble voice mismatches:", failures)));
    }

    @Test
    void hintsRespectEightyCharCap() {
        // REQUIREMENTS.md §10: "Hint length cap ≤80 chars".
        List<String> overflow = new ArrayList<>();
        for (Map.Entry<String, String> e : lang.entrySet()) {
            if (!e.getKey().startsWith("message.questown.chicken.hint.")) {
                continue;
            }
            if (e.getValue().length() > 80) {
                overflow.add(e.getKey() + " (" + e.getValue().length() + " chars): \"" + e.getValue() + "\"");
            }
        }
        Assertions.assertTrue(overflow.isEmpty(),
                "hint copy exceeds 80-char cap: " + overflow);
    }

    @Test
    void plainHintsAreBracketed() {
        // REQUIREMENTS.md §10: bracketed plain-text hints are the explicit
        // fourth-wall fallback. The brackets carry meaning — without them the
        // string reads as monologue, defeating the fallback.
        List<String> bad = new ArrayList<>();
        for (Map.Entry<String, String> e : lang.entrySet()) {
            if (!e.getKey().startsWith("message.questown.chicken.plain.")) {
                continue;
            }
            String v = e.getValue();
            if (!(v.startsWith("[") && v.endsWith("]"))) {
                bad.add(e.getKey() + ": \"" + v + "\"");
            }
        }
        Assertions.assertTrue(bad.isEmpty(),
                "plain-text hints must be wrapped in [...]: " + bad);
    }

    @Test
    void monologueHintsAvoidImperativeAndBrackets() {
        // Voice rule: monologue hints are first-person observation ("It seems
        // to want…", "It looks at…"). Imperative voice ("Place a sign here.")
        // belongs in the bracketed plain fallback. Bracketed monologue hints
        // would show up as plain fallbacks where they shouldn't.
        List<String> bad = new ArrayList<>();
        for (Map.Entry<String, String> e : lang.entrySet()) {
            if (!e.getKey().startsWith("message.questown.chicken.hint.")) {
                continue;
            }
            String v = e.getValue();
            if (v.startsWith("[")) {
                bad.add(e.getKey() + ": monologue hint must not start with '[': \"" + v + "\"");
            }
        }
        Assertions.assertTrue(bad.isEmpty(), String.join("\n  ", bad));
    }

    @Test
    void todo_modRegisteredBubbleStatesAreCoveredByThisSuite() {
        // CLAUDE.md: "If a situation comes up which causes testing to be
        // impossible due to insufficient interfaces on the real code: 1) make
        // a note, 2) add a failing assertion … 3) continue working on what
        // is possible to test."
        //
        // The states below show mod-registered items (TOWN_WAND etc.) in
        // their bubble. Reaching them from a unit test requires Forge mod
        // init — not available from Bootstrap.bootStrap() alone. The voice
        // invariant in noItemHint_mentionsBubbleIconNoun() therefore can't
        // assert them today.
        Assertions.fail("Untested mod-icon beats (need Forge mod-loading harness): "
                + MOD_ICON_STATES);
    }

    // -- token map -------------------------------------------------------

    /**
     * Maps a bubble icon to nouns the corresponding hint must mention. Multi-
     * value lists allow synonyms (e.g. "block" or "cobblestone" or "stone"
     * all satisfy the cobblestone icon). Update when a new vanilla icon is
     * added to {@link ChickenArcBubbles}. Built lazily after
     * {@link Bootstrap#bootStrap()} populates {@link Items}.
     */
    private static Map<Item, List<String>> buildTokenMap() {
        Map<Item, List<String>> m = new LinkedHashMap<>();
        m.put(Items.STICK, List.of("stick"));
        m.put(Items.COBBLESTONE, List.of("block", "cobblestone", "stone"));
        m.put(Items.OAK_DOOR, List.of("door"));
        m.put(Items.OAK_SIGN, List.of("sign"));
        m.put(Items.CHEST, List.of("chest"));
        m.put(Items.CAMPFIRE, List.of("campfire", "fire"));
        m.put(Items.VILLAGER_SPAWN_EGG, List.of("villager"));
        return Map.copyOf(m);
    }

    private static List<String> prepend(String head, List<String> tail) {
        List<String> out = new ArrayList<>(tail.size() + 1);
        out.add(head);
        out.addAll(tail);
        return out;
    }
}

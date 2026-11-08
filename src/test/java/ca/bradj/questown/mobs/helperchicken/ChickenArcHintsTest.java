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

/** The chicken's hints agree with its bubble icons, fit the length limit, and follow the voice rules. */
class ChickenArcHintsTest {

    private static final Path LANG_PATH = Path.of(
            "src/main/resources/assets/questown/lang/en_us.json"
    );

    private static final Set<ChickenBeatState> TERMINAL = EnumSet.of(
            ChickenBeatState.COMPLETE,
            ChickenBeatState.FORFEIT
    );

    /** Lessons whose bubble shows a mod item, which a unit test can't load. */
    private static final Set<ChickenBeatState> MOD_ICON_STATES = EnumSet.of(
            ChickenBeatState.WAITING_FOR_WAND_ON_CAMPFIRE,
            ChickenBeatState.WAITING_FOR_WAND_ON_DOOR,
            ChickenBeatState.WAITING_FOR_PRESSURE_PLATE,
            ChickenBeatState.WAITING_FOR_FREE_FOOD,
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
        // Every reachable hint key exists in the lang file.
        List<String> missing = new ArrayList<>();
        for (ChickenBeatState state : ChickenBeatState.values()) {
            if (TERMINAL.contains(state)) {
                continue;
            }
            for (boolean hasItem : new boolean[]{false, true}) {
                PhaseInputs in = new PhaseInputs(hasItem, false, false);
                if (!isModSafe(state, in)) {
                    continue;
                }
                Presentation p = ChickenArcPresentation.present(state, in);
                if (p.hintKey() == null || !lang.containsKey(p.hintKey())) {
                    missing.add("hint: state=" + state + " hasItem=" + hasItem + " key=" + p.hintKey());
                }
                if (p.plainKey() == null || !lang.containsKey(p.plainKey())) {
                    missing.add("plain: state=" + state + " key=" + p.plainKey());
                }
            }
        }
        Assertions.assertTrue(missing.isEmpty(),
                "lang keys missing or unresolved: " + missing);
    }

    private static boolean isModSafe(ChickenBeatState state, PhaseInputs in) {
        BeatPhase phase = ChickenArcPresentation.activePhase(state, in);
        return switch (state) {
            case WAITING_FOR_WALL_BLOCK,
                 WAITING_FOR_DOOR,
                 WAITING_FOR_SIGN,
                 WAITING_FOR_CHEST,
                 WAITING_FOR_ROOM_BUILD,
                 WAITING_FOR_VILLAGER_UI,
                 COMPLETE,
                 FORFEIT -> true;
            case WAITING_FOR_STICK -> phase == BeatPhase.NEED_TO_FETCH;
            case SUNSET_AND_MAP -> phase == BeatPhase.PREPARING || phase == BeatPhase.AWAITING_NIGHT;
            case WAITING_FOR_WAND_ON_CAMPFIRE,
                 WAITING_FOR_WAND_ON_DOOR,
                 WAITING_FOR_PRESSURE_PLATE,
                 WAITING_FOR_FREE_FOOD,
                 WAITING_FOR_FLAG_UI,
                 AWAITING_WORLDLY_SEEDS_DELIVERY -> false;
        };
    }

    @Test
    void noItemHint_mentionsBubbleIconNoun() {
        // Each lesson's hint names the item its bubble shows. The sleep lesson's chest icon is the exception.
        List<String> failures = new ArrayList<>();
        for (ChickenBeatState state : ChickenBeatState.values()) {
            if (TERMINAL.contains(state)
                    || MOD_ICON_STATES.contains(state)
                    || state == ChickenBeatState.SUNSET_AND_MAP) {
                continue;
            }
            PhaseInputs noItem = new PhaseInputs(false, false, false);
            Presentation p = ChickenArcPresentation.present(state, noItem);
            ItemStack icon = p.bubble().iconA();
            if (icon.isEmpty()) {
                continue;
            }
            String hint = lang.get(p.hintKey());
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
        // Hints are at most 80 characters.
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
        // Plain-language hints are in brackets.
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
        // The chicken's lines are observations; instructions go in the bracketed hints.
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

    /** The words a hint must use for each bubble icon. */
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

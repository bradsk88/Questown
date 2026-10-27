package ca.bradj.questown.town.quests.paths;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

/**
 * The curated-path format and progress rules (plan rulings 3, 4, 18): starters post at the fork,
 * a batch posts once its opens_after batches are complete, a path is taken once a starter is done.
 */
class QuestPathTest {

    private static final String OUTFITTER_SHAPE = """
            {"id": "questown:outfitter", "batches": [
              {"id": "start", "arrival": true, "quests": [
                {"room": "questown:crafting_room"}, {"job_change": "crafter/stick"},
                {"loot": "minecraft:stick", "count": 8}]},
              {"id": "armor", "opens_after": ["start"], "quests": [{"room": "questown:armory"}]},
              {"id": "weapons_1", "opens_after": ["start"], "quests": [{"skill": "crafter/wooden_shovel"}]},
              {"id": "weapons_2", "opens_after": ["weapons_1"], "arrival": true,
               "quests": [{"loot": "minecraft:stone_sword", "count": 1}]}
            ]}
            """;

    private static QuestPath parse(String json) {
        return QuestPathParser.parse(new Gson().fromJson(json, JsonObject.class));
    }

    @Test
    void parse_readsBatchesQuestsAndArrivals() {
        QuestPath p = parse(OUTFITTER_SHAPE);

        Assertions.assertEquals("questown:outfitter", p.id());
        Assertions.assertEquals(List.of("start", "armor", "weapons_1", "weapons_2"),
                p.batches().stream().map(QuestPath.Batch::id).toList());
        QuestPath.Batch start = p.batches().get(0);
        Assertions.assertTrue(start.isStarter());
        Assertions.assertTrue(start.arrival());
        Assertions.assertEquals(new QuestPath.Step(QuestPath.Kind.LOOT, "minecraft:stick", 8), start.quests().get(2));
        Assertions.assertEquals(new QuestPath.Step(QuestPath.Kind.JOB_CHANGE, "crafter/stick", 1), start.quests().get(1));
        Assertions.assertFalse(p.batches().get(1).arrival());
        Assertions.assertEquals("questown:outfitter/armor", p.batchTag(p.batches().get(1)));
    }

    @Test
    void parse_readsALevelQuest_rootAndLevel() {
        QuestPath p = parse("""
                {"id": "x", "batches": [{"id": "a", "quests": [{"level": "crafter", "at": 3}]}]}""");

        Assertions.assertEquals(new QuestPath.Step(QuestPath.Kind.LEVEL, "crafter", 3), p.batches().get(0).quests().get(0));
        Assertions.assertEquals(List.of(), QuestPathValidator.errors(p));
    }

    @Test
    void parse_rejectsALevelQuestWithNoLevel() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> parse("""
                {"id": "x", "batches": [{"id": "a", "quests": [{"level": "crafter"}]}]}"""));
    }

    @Test
    void validator_flagsALevelQuestNamingAJobInsteadOfARoot() {
        QuestPath p = parse("""
                {"id": "x", "batches": [{"id": "a", "quests": [{"level": "crafter/stick", "at": 3}]}]}""");

        Assertions.assertTrue(QuestPathValidator.errors(p).stream().anyMatch(e -> e.contains("job root")));
    }

    @Test
    void parse_rejectsAQuestWithNoKind() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> parse("""
                {"id": "x", "batches": [{"id": "a", "quests": [{"count": 3}]}]}"""));
    }

    @Test
    void validator_acceptsTheOutfitterShape() {
        Assertions.assertEquals(List.of(), QuestPathValidator.errors(parse(OUTFITTER_SHAPE)));
    }

    @Test
    void validator_flagsUnknownOpensAfter_cycles_noStarter_badJobIds() {
        QuestPath p = parse("""
                {"id": "x", "batches": [
                  {"id": "a", "opens_after": ["b"], "quests": [{"skill": "crafter"}]},
                  {"id": "b", "opens_after": ["a", "zzz"], "quests": [{"loot": "minecraft:stick", "count": 0}]}
                ]}""");

        List<String> errors = QuestPathValidator.errors(p);

        Assertions.assertTrue(errors.stream().anyMatch(e -> e.contains("no starter")), errors.toString());
        Assertions.assertTrue(errors.stream().anyMatch(e -> e.contains("unknown batch zzz")), errors.toString());
        Assertions.assertTrue(errors.stream().anyMatch(e -> e.contains("cycle")), errors.toString());
        Assertions.assertTrue(errors.stream().anyMatch(e -> e.contains("root/job")), errors.toString());
        Assertions.assertTrue(errors.stream().anyMatch(e -> e.contains("count")), errors.toString());
    }

    @Test
    void progress_startersFirst_thenBranchesInParallel_thenTheirFollowUps() {
        QuestPath p = parse(OUTFITTER_SHAPE);

        Assertions.assertEquals(List.of("start"), ids(QuestPathProgress.toPost(p, Set.of(), Set.of())));
        Assertions.assertEquals(List.of(), ids(QuestPathProgress.toPost(p, Set.of("start"), Set.of())));
        Assertions.assertEquals(List.of("armor", "weapons_1"),
                ids(QuestPathProgress.toPost(p, Set.of("start"), Set.of("start"))));
        Assertions.assertEquals(List.of("weapons_2"),
                ids(QuestPathProgress.toPost(p, Set.of("start", "armor", "weapons_1"), Set.of("start", "weapons_1"))));
    }

    @Test
    void progress_takenOnceAStarterIsDone_finishedWhenNothingIsLeft() {
        QuestPath p = parse(OUTFITTER_SHAPE);
        Set<String> all = Set.of("start", "armor", "weapons_1", "weapons_2");

        Assertions.assertFalse(QuestPathProgress.taken(p, Set.of()));
        Assertions.assertTrue(QuestPathProgress.taken(p, Set.of("start")));
        Assertions.assertFalse(QuestPathProgress.finished(p, Set.of("start"), Set.of("start")));
        Assertions.assertFalse(QuestPathProgress.finished(p, all, Set.of("start", "armor", "weapons_1")));
        Assertions.assertTrue(QuestPathProgress.finished(p, all, all));
    }

    private static List<String> ids(List<QuestPath.Batch> batches) {
        return batches.stream().map(QuestPath.Batch::id).toList();
    }
}

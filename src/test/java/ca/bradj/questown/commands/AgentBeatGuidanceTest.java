package ca.bradj.questown.commands;

import ca.bradj.questown.mobs.helperchicken.ChickenBeatState;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Coverage for {@link AgentBeatGuidance}, the agent-facing restatement of the
 * helper-chicken onboarding beats. Pure enum logic, no Forge lifecycle needed.
 */
class AgentBeatGuidanceTest {

    private static final ChickenBeatState[] WALKABLE = {
            ChickenBeatState.WAITING_FOR_STICK,
            ChickenBeatState.WAITING_FOR_WAND_ON_CAMPFIRE,
            ChickenBeatState.SUNSET_AND_MAP,
            ChickenBeatState.WAITING_FOR_WALL_BLOCK,
            ChickenBeatState.WAITING_FOR_DOOR,
            ChickenBeatState.WAITING_FOR_WAND_ON_DOOR,
            ChickenBeatState.WAITING_FOR_SIGN,
            ChickenBeatState.WAITING_FOR_CHEST,
            ChickenBeatState.WAITING_FOR_PRESSURE_PLATE,
            ChickenBeatState.WAITING_FOR_FREE_FOOD,
            ChickenBeatState.WAITING_FOR_VILLAGER_UI,
            ChickenBeatState.WAITING_FOR_FLAG_UI,
            ChickenBeatState.AWAITING_WORLDLY_SEEDS_DELIVERY
    };

    @Test
    void everyWalkableBeatHasGuidance() {
        for (ChickenBeatState state : WALKABLE) {
            AgentBeatGuidance.Guidance g = AgentBeatGuidance.forBeat(state);
            Assertions.assertNotNull(g, "guidance missing for " + state);
            Assertions.assertFalse(g.need().isBlank(), "blank need for " + state);
            Assertions.assertFalse(g.how().isBlank(), "blank how for " + state);
        }
    }

    @Test
    void terminalBeatsHaveNoGuidance() {
        Assertions.assertNull(AgentBeatGuidance.forBeat(ChickenBeatState.COMPLETE));
        Assertions.assertNull(AgentBeatGuidance.forBeat(ChickenBeatState.FORFEIT));
    }

    @Test
    void beatsWithADevCommandNameIt() {
        // The agent cannot right-click, so beats that have a _qtdev command
        // must name it in the "how".
        Assertions.assertTrue(
                AgentBeatGuidance.forBeat(ChickenBeatState.WAITING_FOR_WAND_ON_DOOR).how()
                        .contains("registerroom"));
        Assertions.assertTrue(
                AgentBeatGuidance.forBeat(ChickenBeatState.WAITING_FOR_SIGN).how()
                        .contains("place_board"));
        Assertions.assertTrue(
                AgentBeatGuidance.forBeat(ChickenBeatState.WAITING_FOR_PRESSURE_PLATE).how()
                        .contains("place_mat"));
        Assertions.assertTrue(
                AgentBeatGuidance.forBeat(ChickenBeatState.AWAITING_WORLDLY_SEEDS_DELIVERY).how()
                        .contains("deposit"));
    }

    @Test
    void beatsWithNoDevCommandFlagThemForAPlayer() {
        // The right-click beats with no _qtdev command are the stumbling points:
        // the guidance must say a player action is needed, not silently omit it.
        Assertions.assertTrue(
                AgentBeatGuidance.forBeat(ChickenBeatState.WAITING_FOR_WAND_ON_CAMPFIRE).how()
                        .contains("player action"));
        Assertions.assertTrue(
                AgentBeatGuidance.forBeat(ChickenBeatState.WAITING_FOR_VILLAGER_UI).how()
                        .contains("player action"));
        Assertions.assertTrue(
                AgentBeatGuidance.forBeat(ChickenBeatState.WAITING_FOR_FLAG_UI).how()
                        .contains("player action"));
    }

    @Test
    void freeFoodBeatIsOptional() {
        Assertions.assertTrue(
                AgentBeatGuidance.forBeat(ChickenBeatState.WAITING_FOR_FREE_FOOD).how()
                        .toLowerCase().contains("skip"));
    }

    @Test
    void mapLineListsEveryWalkableBeatAndMarksTheCurrent() {
        for (ChickenBeatState current : WALKABLE) {
            String map = AgentBeatGuidance.mapLine(current);
            // Every walkable beat's short label appears, exactly once marked.
            for (ChickenBeatState beat : WALKABLE) {
                Assertions.assertTrue(
                        map.contains(AgentBeatGuidance.shortLabel(beat)),
                        "map missing label for " + beat);
            }
            String marked = "*" + AgentBeatGuidance.shortLabel(current) + "*";
            Assertions.assertTrue(map.contains(marked), "current not marked: " + map);
        }
    }

    @Test
    void walkableIndexAndCount() {
        Assertions.assertEquals(13, AgentBeatGuidance.walkableCount());
        Assertions.assertEquals(1, AgentBeatGuidance.walkableIndex(WALKABLE[0]));
        Assertions.assertEquals(13, AgentBeatGuidance.walkableIndex(WALKABLE[12]));
        Assertions.assertEquals(0, AgentBeatGuidance.walkableIndex(ChickenBeatState.COMPLETE));
        Assertions.assertEquals(0, AgentBeatGuidance.walkableIndex(ChickenBeatState.FORFEIT));
    }
}

package ca.bradj.questown.commands.test;

import ca.bradj.questown.mobs.helperchicken.ChickenBeatState;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

class ChickenArcBlueprintRegistryTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void all_returnsExactlyThirteenScenarios() {
        List<ChickenArcBlueprint> all = ChickenArcBlueprintRegistry.all();
        Assertions.assertEquals(
                ChickenArcBlueprintRegistry.EXPECTED_SCENARIO_COUNT,
                all.size(),
                "registry must contain exactly 13 scenarios as authored in the plan"
        );
    }

    @Test
    void all_scenarioNamesAreUnique() {
        List<ChickenArcBlueprint> all = ChickenArcBlueprintRegistry.all();
        Set<String> names = new HashSet<>();
        for (ChickenArcBlueprint bp : all) {
            Assertions.assertTrue(
                    names.add(bp.name()),
                    "duplicate scenario name: " + bp.name()
            );
        }
    }

    @Test
    void all_scenarioNamesMatchPlanExactly() {
        // Agent runbook greps these names verbatim — any rename must also update
        // the runbook. Intentional: makes renames loud.
        List<String> expected = List.of(
                "stick_peck_and_follow_spawn",
                "F1_stick_to_campfire",
                "F3_build_room_to_welcome_mat",
                "F4_seeds_to_statue",
                "F1_rotation_clockwise_90",
                "rotation_ambiguity_forfeit",
                "rotation_zero_match_forfeit",
                "two_flags_independent_arcs",
                "skip_chicken_command",
                "forfeit_remove_command",
                "first_gather_worldly_seeds_realtime",
                "first_gather_worldly_seeds_warp",
                "wand_on_unlit_campfire_outside_flag"
        );
        List<String> actual = ChickenArcBlueprintRegistry.all().stream()
                .map(ChickenArcBlueprint::name)
                .toList();
        Assertions.assertEquals(expected, actual);
    }

    @Test
    void all_everyScenarioIsChickenCategory() {
        for (ChickenArcBlueprint bp : ChickenArcBlueprintRegistry.all()) {
            Assertions.assertEquals(
                    ChickenArcBlueprintRegistry.CATEGORY,
                    bp.category(),
                    "scenario " + bp.name() + " must be categorized 'chicken'"
            );
        }
    }

    @Test
    void all_finalBeatStatesWhenPresentAreValidEnumValues() {
        Set<ChickenBeatState> valid = new HashSet<>(Arrays.asList(ChickenBeatState.values()));
        for (ChickenArcBlueprint bp : ChickenArcBlueprintRegistry.all()) {
            bp.expectation().finalBeatState().ifPresent(beat ->
                    Assertions.assertTrue(
                            valid.contains(beat),
                            "scenario " + bp.name() + " has invalid beat state " + beat
                    )
            );
        }
    }

    @Test
    void f4SeedsToStatue_expectsCompleteAndStatueAndDiscarded() {
        ChickenArcBlueprint bp = findByName("F4_seeds_to_statue");
        Assertions.assertEquals(
                ChickenBeatState.COMPLETE,
                bp.expectation().finalBeatState().orElseThrow()
        );
        Assertions.assertTrue(bp.expectation().expectStatuePlaced());
        Assertions.assertTrue(bp.expectation().expectChickenDiscarded());
    }

    @Test
    void forfeitScenarios_expectArcForfeitTrue() {
        for (String name : List.of(
                "rotation_ambiguity_forfeit",
                "rotation_zero_match_forfeit",
                "forfeit_remove_command"
        )) {
            ChickenArcBlueprint bp = findByName(name);
            Assertions.assertEquals(
                    Boolean.TRUE,
                    bp.expectation().expectedFlagBits().get("chicken-arc-forfeit"),
                    name + " must assert chicken-arc-forfeit=true"
            );
        }
    }

    @Test
    void skipChickenCommand_expectsNoSpawnAndEverSpawnedFalse() {
        ChickenArcBlueprint bp = findByName("skip_chicken_command");
        Assertions.assertFalse(bp.expectation().expectChickenSpawned());
        Assertions.assertEquals(
                Boolean.FALSE,
                bp.expectation().expectedFlagBits().get("chicken-ever-spawned"),
                "skip_chicken_command: command-placed flag is chicken-ineligible, so chicken-ever-spawned stays false"
        );
        Assertions.assertTrue(
                bp.placeFlagViaCommand(),
                "skip_chicken_command must set placeFlagViaCommand so the default PLACE_FLAG phase is skipped"
        );
    }

    @Test
    void rotationForfeits_disableForceRotationDetected() {
        for (String name : List.of(
                "rotation_ambiguity_forfeit",
                "rotation_zero_match_forfeit"
        )) {
            ChickenArcBlueprint bp = findByName(name);
            Assertions.assertFalse(
                    bp.forceRotationDetected(),
                    name + " must let the real HelperChickenRotationDetector tick"
            );
        }
    }

    /**
     * Every {@link ChickenArcScriptedAction} subtype must be exercised by at
     * least one registered scenario. If a subtype is reachable only in tests
     * but never from the registry, we're not actually verifying the executor's
     * handler for it at runtime.
     */
    @Test
    void everyScriptedActionSubtype_isReachableInRegistry() {
        Set<Class<?>> used = new HashSet<>();
        for (ChickenArcBlueprint bp : ChickenArcBlueprintRegistry.all()) {
            for (ChickenArcScriptedAction action : bp.scriptedActions()) {
                used.add(action.getClass());
            }
        }
        Class<?>[] permitted = ChickenArcScriptedAction.class.getPermittedSubclasses();
        for (Class<?> sub : permitted) {
            Assertions.assertTrue(
                    used.contains(sub),
                    "no registered scenario uses " + sub.getSimpleName()
                            + " — every sealed subtype must be reachable from the registry"
            );
        }
    }

    @Test
    void getTestsByCategory_chicken_returnsAllThirteen() {
        List<ChickenArcBlueprint> filtered =
                ChickenArcBlueprintRegistry.getTestsByCategory("chicken");
        Assertions.assertEquals(
                ChickenArcBlueprintRegistry.EXPECTED_SCENARIO_COUNT,
                filtered.size()
        );
    }

    @Test
    void getTestsByCategory_jobs_returnsZero() {
        List<ChickenArcBlueprint> filtered =
                ChickenArcBlueprintRegistry.getTestsByCategory("jobs");
        Assertions.assertEquals(0, filtered.size());
    }

    @Test
    void getTestsByCategory_null_returnsAll() {
        List<ChickenArcBlueprint> filtered =
                ChickenArcBlueprintRegistry.getTestsByCategory(null);
        Assertions.assertEquals(
                ChickenArcBlueprintRegistry.EXPECTED_SCENARIO_COUNT,
                filtered.size()
        );
    }

    @Test
    void twoFlagsIndependentArcs_usesOverrideHalfWidth() {
        ChickenArcBlueprint bp = findByName("two_flags_independent_arcs");
        Assertions.assertTrue(
                bp.effectiveHalfWidth() >= 20,
                "two_flags scenario must use a wide arena"
        );
    }

    private ChickenArcBlueprint findByName(String name) {
        return ChickenArcBlueprintRegistry.all().stream()
                .filter(bp -> bp.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("scenario " + name + " not in registry"));
    }
}

package ca.bradj.questown.commands.test;

import net.minecraft.world.level.block.Rotation;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Declarative shape of one chicken-arc scenario for the headless
 * {@code ChickenArcTestExecutor}.
 *
 * <p>Carries the arena setup knobs (rotation, flag-placement mode, arena size),
 * an ordered list of scripted player actions, the expectation bundle, and
 * post-action settle / warp controls. No job-specific fields — the existing
 * {@code TestBlueprint} handles jobs; this record is deliberately parallel
 * rather than an overload so the two tracks keep distinct contracts.
 *
 * @param name                  stable scenario name used in the log prefix
 * @param category              always {@code "chicken"}; mirrors
 *                              {@code TestBlueprintRegistry.getTestsByCategory} filter shape
 * @param startRotation         rotation written to {@code chicken-structure-rotation}
 *                              (happy path) or the rotation whose anchor the forfeit
 *                              scenario builds toward (detector path)
 * @param placeFlagViaCommand   when {@code true}, the executor skips its default
 *                              {@code PLACE_FLAG} phase and relies on a scripted
 *                              {@code RunCommand("/qt flag place_above ...")} action
 * @param forceRotationDetected when {@code true}, the executor writes
 *                              {@code chicken-rotation-detected=true} + rotation bits
 *                              on the flag BE before the spawn controller ticks; when
 *                              {@code false}, the real
 *                              {@code HelperChickenRotationDetector.tick()} runs to
 *                              completion
 * @param scriptedActions       ordered player actions; dispatched one at a time with
 *                              each action's {@code postActionWaitTicks} between
 * @param expectation           assertion bundle consumed by the checker
 * @param postActionSettleTicks final settle after the last scripted action, before
 *                              {@code WARP} runs
 * @param warpAmount            ticks to fast-forward via {@code flag.warpTime}; 0 skips
 * @param halfWidthOverride     optional per-scenario arena half-width override; when
 *                              null, the executor defaults to 10
 */
public record ChickenArcBlueprint(
        String name,
        String category,
        Rotation startRotation,
        boolean placeFlagViaCommand,
        boolean forceRotationDetected,
        List<ChickenArcScriptedAction> scriptedActions,
        ChickenArcExpectation expectation,
        int postActionSettleTicks,
        int warpAmount,
        @Nullable Integer halfWidthOverride
) {
    public ChickenArcBlueprint {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("blueprint name is required");
        }
        if (category == null || category.isBlank()) {
            throw new IllegalArgumentException("category is required");
        }
        if (startRotation == null) {
            throw new IllegalArgumentException("startRotation is required");
        }
        if (expectation == null) {
            throw new IllegalArgumentException("expectation is required");
        }
        scriptedActions = scriptedActions == null
                ? List.of()
                : List.copyOf(scriptedActions);
    }

    public int effectiveHalfWidth() {
        return halfWidthOverride != null ? halfWidthOverride : 10;
    }

    public static Builder builder(String name) {
        return new Builder(name);
    }

    public static final class Builder {
        private final String name;
        private String category = "chicken";
        private Rotation startRotation = Rotation.NONE;
        private boolean placeFlagViaCommand;
        private boolean forceRotationDetected = true;
        private List<ChickenArcScriptedAction> scriptedActions = List.of();
        private ChickenArcExpectation expectation = ChickenArcExpectation.builder().build();
        private int postActionSettleTicks = 60;
        private int warpAmount;
        private @Nullable Integer halfWidthOverride;

        private Builder(String name) {
            this.name = name;
        }

        public Builder category(String v) {
            this.category = v;
            return this;
        }

        public Builder rotation(Rotation v) {
            this.startRotation = v;
            return this;
        }

        public Builder placeFlagViaCommand(boolean v) {
            this.placeFlagViaCommand = v;
            return this;
        }

        public Builder forceRotationDetected(boolean v) {
            this.forceRotationDetected = v;
            return this;
        }

        public Builder actions(List<ChickenArcScriptedAction> v) {
            this.scriptedActions = List.copyOf(v);
            return this;
        }

        public Builder expectation(ChickenArcExpectation v) {
            this.expectation = v;
            return this;
        }

        public Builder postActionSettleTicks(int v) {
            this.postActionSettleTicks = v;
            return this;
        }

        public Builder warpAmount(int v) {
            this.warpAmount = v;
            return this;
        }

        public Builder halfWidthOverride(int v) {
            this.halfWidthOverride = v;
            return this;
        }

        public ChickenArcBlueprint build() {
            return new ChickenArcBlueprint(
                    name,
                    category,
                    startRotation,
                    placeFlagViaCommand,
                    forceRotationDetected,
                    scriptedActions,
                    expectation,
                    postActionSettleTicks,
                    warpAmount,
                    halfWidthOverride
            );
        }
    }
}

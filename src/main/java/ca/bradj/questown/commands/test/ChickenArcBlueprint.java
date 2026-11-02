package ca.bradj.questown.commands.test;

import net.minecraft.world.level.block.Rotation;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * One helper-chicken autotest scenario: the arena setup, the scripted player actions, and what
 * must be true at the end.
 *
 * @param placeFlagViaCommand   the scenario places the flag with a scripted command
 * @param forceRotationDetected skip the real rotation detection and write the rotation directly
 * @param warpAmount            ticks to warp at the end; 0 for none
 * @param halfWidthOverride     arena half-width; null for the default of 10
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

package ca.bradj.questown.commands.test;

import ca.bradj.questown.mobs.helperchicken.ChickenBeatState;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;

/**
 * Bundle of assertions for a chicken-arc scenario. The {@code ChickenArcResultChecker}
 * reads these and emits one unified log line per assertion.
 *
 * <p>Item-count deltas are delegated to {@link TestExpectation} via composition; the
 * existing {@code TestResultChecker} handles them. Everything else is chicken-arc
 * specific and checked by {@code ChickenArcResultChecker} directly.
 */
public record ChickenArcExpectation(
        @Nullable ChickenBeatState expectedFinalBeatState,
        Map<String, Boolean> expectedFlagBits,
        boolean expectStatuePlaced,
        boolean expectChickenDiscarded,
        boolean expectChickenSpawned,
        @Nullable TestExpectation itemDeltas
) {
    public ChickenArcExpectation {
        // Defensive immutable copy so holders cannot mutate after construction.
        expectedFlagBits = expectedFlagBits == null
                ? Collections.emptyMap()
                : Map.copyOf(expectedFlagBits);
    }

    public Optional<ChickenBeatState> finalBeatState() {
        return Optional.ofNullable(expectedFinalBeatState);
    }

    public Optional<TestExpectation> itemDeltasOpt() {
        return Optional.ofNullable(itemDeltas);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private @Nullable ChickenBeatState expectedFinalBeatState;
        private Map<String, Boolean> expectedFlagBits = Collections.emptyMap();
        private boolean expectStatuePlaced;
        private boolean expectChickenDiscarded;
        private boolean expectChickenSpawned = true;
        private @Nullable TestExpectation itemDeltas;

        public Builder finalBeat(ChickenBeatState beat) {
            this.expectedFinalBeatState = beat;
            return this;
        }

        public Builder flagBits(Map<String, Boolean> bits) {
            this.expectedFlagBits = Map.copyOf(bits);
            return this;
        }

        public Builder statuePlaced(boolean v) {
            this.expectStatuePlaced = v;
            return this;
        }

        public Builder chickenDiscarded(boolean v) {
            this.expectChickenDiscarded = v;
            return this;
        }

        public Builder chickenSpawned(boolean v) {
            this.expectChickenSpawned = v;
            return this;
        }

        public Builder itemDeltas(TestExpectation te) {
            this.itemDeltas = te;
            return this;
        }

        public ChickenArcExpectation build() {
            return new ChickenArcExpectation(
                    expectedFinalBeatState,
                    expectedFlagBits,
                    expectStatuePlaced,
                    expectChickenDiscarded,
                    expectChickenSpawned,
                    itemDeltas
            );
        }
    }
}

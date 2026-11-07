package ca.bradj.questown.commands.test;

import ca.bradj.questown.mobs.helperchicken.BeatPhase;
import ca.bradj.questown.mobs.helperchicken.ChickenBeatState;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

class ChickenArcExpectationTest {

    @Test
    void finalPhase_setOnBuilder_isPresentAfterBuild() {
        ChickenArcExpectation e = ChickenArcExpectation.builder()
                .finalPhase(BeatPhase.READY_TO_USE)
                .build();
        Assertions.assertEquals(Optional.of(BeatPhase.READY_TO_USE), e.finalPhase());
    }

    @Test
    void finalPhase_unset_isEmpty() {
        ChickenArcExpectation e = ChickenArcExpectation.builder().build();
        Assertions.assertEquals(Optional.empty(), e.finalPhase());
    }

    @Test
    void finalPhase_doesNotInteractWithOtherFields() {
        ChickenArcExpectation e = ChickenArcExpectation.builder()
                .finalBeat(ChickenBeatState.WAITING_FOR_STICK)
                .flagBits(Map.of("chicken-arc-forfeit", false))
                .finalPhase(BeatPhase.NEED_TO_FETCH)
                .build();
        Assertions.assertEquals(
                Optional.of(ChickenBeatState.WAITING_FOR_STICK), e.finalBeatState());
        Assertions.assertEquals(
                Boolean.FALSE, e.expectedFlagBits().get("chicken-arc-forfeit"));
        Assertions.assertEquals(Optional.of(BeatPhase.NEED_TO_FETCH), e.finalPhase());
    }
}

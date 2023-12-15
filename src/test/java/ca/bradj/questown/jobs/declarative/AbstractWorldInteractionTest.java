package ca.bradj.questown.jobs.declarative;

import ca.bradj.questown.jobs.*;
import ca.bradj.questown.jobs.production.ProductionStatus;
import ca.bradj.questown.town.WorkStatusStore.State;
import ca.bradj.roomrecipes.core.space.Position;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

class AbstractWorldInteractionTest {

    private static class TestWI extends AbstractWorldInteraction<Void, Position, GathererJournalTest.TestItem, GathererJournalTest.TestItem> {

        private boolean processed;
        private boolean inserted;
        private boolean extracted;
        private final HashMap<Position, State> state = new HashMap<>();

        public TestWI(
                int maxState,
                ImmutableMap<Integer, Function<GathererJournalTest.TestItem, Boolean>> toolsRequiredAtStates,
                ImmutableMap<Integer, Integer> workRequiredAtStates,
                ImmutableMap<Integer, Function<GathererJournalTest.TestItem, Boolean>> ingredientsRequiredAtStates,
                Supplier<Collection<GathererJournalTest.TestItem>> journal
        ) {
            super(
                    0,
                    maxState,
                    toolsRequiredAtStates,
                    workRequiredAtStates,
                    ingredientsRequiredAtStates,
                    journal
            );
        }

        @Override
        protected boolean tryProcessOre(
                Void unused,
                WorkSpot<Integer, Position> workSpot
        ) {
            processed = true;
            advance(workSpot);
            return true;
        }

        @Override
        protected ItemWI<Position, Void> getItemWI() {
            return (unused, workSpot) -> {
                inserted = true;
                advance(workSpot);
                return true;
            };
        }

        private void advance(WorkSpot<Integer, Position> workSpot) {
            state.compute(workSpot.position, (k, v) -> {
                if (v == null) {
                    v = State.fresh();
                }
                return v.incrProcessing();
            });
        }

        @Override
        protected void setJobBlockState(
                Void unused,
                Position position,
                State state
        ) {
            this.state.put(position, state);
        }

        @Override
        protected State getJobBlockState(
                Void unused,
                Position position
        ) {
            return this.state.get(position);
        }

        @Override
        protected boolean tryExtractOre(
                Void unused,
                Position position
        ) {
            extracted = true;
            return true;
        }

        @Override
        protected boolean isEntityClose(
                Void unused,
                Position position
        ) {
            return true;
        }

        @Override
        protected boolean isReady(Void unused) {
            return true;
        }

        @Override
        public Map<Integer, Integer> ingredientQuantityRequiredAtStates() {
            return null;
        }
    }

    private static class J extends ProductionJournal<GathererJournalTest.TestItem, GathererJournalTest.TestItem> {

        public J(
                @NotNull JobID jobId,
                SignalSource sigs,
                int capacity,
                EmptyFactory<GathererJournalTest.TestItem> ef,
                IStatusFactory<ProductionStatus> sf
        ) {
            super(jobId, sigs, capacity, ef, sf);
        }
    }

    @Test
    void Test_ShouldDoNothingForCompletelyEmptyWork() {
        TestWI wi = new TestWI(
                3,
                ImmutableMap.of(),
                ImmutableMap.of(),
                ImmutableMap.of(),
                () -> ImmutableList.of(new GathererJournalTest.TestItem(""))
        );
        wi.tryWorking(null, arbitrarySpot(0));

        Assertions.assertFalse(wi.extracted);
        Assertions.assertFalse(wi.inserted);
        Assertions.assertFalse(wi.processed);
    }

    @Test
    void Test_ShouldInsertForWorklessToollessJob() {
        TestWI wi = new TestWI(
                3,
                ImmutableMap.of(), // No tools required
                ImmutableMap.of(), // No work required
                ImmutableMap.of(
                        0, (i) -> "grapes".equals(i.value) // Grapes required
                ),
                () -> ImmutableList.of(new GathererJournalTest.TestItem(""))
        );
        wi.tryWorking(null, arbitrarySpot(0));

        Assertions.assertFalse(wi.extracted);
        Assertions.assertFalse(wi.processed);
        Assertions.assertTrue(wi.inserted);
    }

    @Test
    void Test_ShouldInsertAndProcessForToollessJob() {
        TestWI wi = new TestWI(
                3,
                ImmutableMap.of(), // No tools required
                ImmutableMap.of(
                        0, 100 // 100 work required
                ),
                ImmutableMap.of(
                        0, (i) -> "grapes".equals(i.value) // Grapes required
                ),
                () -> ImmutableList.of(new GathererJournalTest.TestItem(""))
        );
        wi.tryWorking(null, arbitrarySpot(0));

        Assertions.assertFalse(wi.extracted);
        Assertions.assertTrue(wi.inserted);
        Assertions.assertTrue(wi.processed);
    }

    @Test
    void Test_ShouldInsertAndNotProcess_ForJobWithTwoStages_OnFirstTry() {
        TestWI wi = new TestWI(
                3,
                ImmutableMap.of(), // No tools required
                ImmutableMap.of(
                        0, 0, // No work required at stage 0
                        1, 100 // 100 work required
                ),
                ImmutableMap.of(
                        0, (i) -> "grapes".equals(i.value) // Grapes required at stage 0
                ),
                () -> ImmutableList.of(new GathererJournalTest.TestItem(""))
        );
        wi.tryWorking(null, arbitrarySpot(0));

        Assertions.assertFalse(wi.extracted);
        Assertions.assertFalse(wi.processed);
        Assertions.assertTrue(wi.inserted);
    }

    @Test
    void Test_MetaShouldMoveToStage2_AfterFirstTry() {
        TestWI wi = new TestWI(
                3,
                ImmutableMap.of(), // No tools required
                ImmutableMap.of(
                        0, 0, // No work required at stage 0
                        1, 100 // 100 work required
                ),
                ImmutableMap.of(
                        0, (i) -> "grapes".equals(i.value) // Grapes required at stage 0
                ),
                () -> ImmutableList.of(new GathererJournalTest.TestItem(""))
        );
        WorkSpot<Integer, Position> spot = arbitrarySpot(0);

        Assertions.assertEquals(0, wi.state.getOrDefault(spot.position, State.fresh()).processingState());

        wi.tryWorking(null, spot);

        Assertions.assertEquals(1, wi.state.get(spot.position).processingState());
        Assertions.assertFalse(wi.processed);
        Assertions.assertTrue(wi.inserted);
    }


    @Test
    void Test_ShouldInsertAndProcess_ForJobWithTwoStages_AfterSecondTry() {
        TestWI wi = new TestWI(
                2,
                ImmutableMap.of(), // No tools required
                ImmutableMap.of(
                        0, 0, // No work required at stage 0
                        1, 100 // 100 work required
                ),
                ImmutableMap.of(
                        0, (i) -> "grapes".equals(i.value) // Grapes required at stage 0
                ),
                () -> ImmutableList.of(new GathererJournalTest.TestItem(""))
        );

        wi.tryWorking(null, arbitrarySpot(0));
        wi.tryWorking(null, arbitrarySpot(1));

        Assertions.assertFalse(wi.extracted);
        Assertions.assertTrue(wi.processed);
        Assertions.assertTrue(wi.inserted);
    }

    @Test
    void Test_ShouldInsertAndProcessAndExtract_ForJobWithThreeStages_AfterThirdTry() {
        TestWI wi = new TestWI(
                2,
                ImmutableMap.of(), // No tools required
                ImmutableMap.of(
                        0, 0, // No work required at stage 0
                        1, 100 // 100 work required
                ),
                ImmutableMap.of(
                        0, (i) -> "grapes".equals(i.value) // Grapes required at stage 0
                ),
                () -> ImmutableList.of(new GathererJournalTest.TestItem(""))
        );

        wi.tryWorking(null, arbitrarySpot(0));
        wi.tryWorking(null, arbitrarySpot(1));
        wi.tryWorking(null, arbitrarySpot(2));

        Assertions.assertTrue(wi.inserted);
        Assertions.assertTrue(wi.processed);
        Assertions.assertTrue(wi.extracted);
    }

    @Test
    void Test_ShouldDoNothingIfToolIsRequiredButNotHad() {
        TestWI wi = new TestWI(
                2,
                ImmutableMap.of(
                        0, (i) -> false // Villager does not have the needed tool
                ),
                ImmutableMap.of(
                        0, 0 // No work required at stage 0
                ),
                ImmutableMap.of(
                        0, (i) -> true // Villager has all the items needed
                ),
                () -> ImmutableList.of(new GathererJournalTest.TestItem(""))
        );

        wi.tryWorking(null, arbitrarySpot(0));

        Assertions.assertFalse(wi.inserted);
        Assertions.assertFalse(wi.processed);
        Assertions.assertFalse(wi.extracted);
    }


    @Test
    void Test_ShouldInsertAndProcessAndExtract_WhenToolsRequiredAndPossessed_ForJobWithThreeStages_AfterThirdTry() {
        TestWI wi = new TestWI(
                2,
                ImmutableMap.of(
                        0, (i) -> true, // Villager has the needed tools
                        1, (i) -> true, // Villager has the needed tools
                        2, (i) -> true // Villager has the needed tools
                ),
                ImmutableMap.of(
                        0, 0, // No work required at stage 0
                        1, 100 // 100 work required
                ),
                ImmutableMap.of(
                        0, (i) -> "grapes".equals(i.value) // Grapes required at stage 0
                ),
                () -> ImmutableList.of(new GathererJournalTest.TestItem(""))
        );

        wi.tryWorking(null, arbitrarySpot(0));
        wi.tryWorking(null, arbitrarySpot(1));
        wi.tryWorking(null, arbitrarySpot(2));

        Assertions.assertTrue(wi.inserted);
        Assertions.assertTrue(wi.processed);
        Assertions.assertTrue(wi.extracted);
    }

    @NotNull
    private static WorkSpot<Integer, Position> arbitrarySpot(int state) {
        return new WorkSpot<>(new Position(1, 2), state, 0);
    }

}
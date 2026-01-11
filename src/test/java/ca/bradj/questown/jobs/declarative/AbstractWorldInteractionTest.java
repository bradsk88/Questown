package ca.bradj.questown.jobs.declarative;

import ca.bradj.questown.jobs.GathererJournalTest;
import ca.bradj.questown.jobs.WorkOutput;
import ca.bradj.questown.jobs.WorkPosition;
import ca.bradj.questown.logic.IPredicateCollection;
import ca.bradj.questown.logic.MonoPredicateCollection;
import ca.bradj.questown.town.Claim;
import ca.bradj.questown.town.interfaces.ImmutableWorkStateContainer;
import ca.bradj.questown.town.workstatus.State;
import ca.bradj.roomrecipes.core.space.Position;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import static ca.bradj.questown.jobs.declarative.AbstractItemWITest.alwaysFalse;
import static ca.bradj.questown.jobs.declarative.AbstractItemWITest.alwaysTrue;

class AbstractWorldInteractionTest {

    // === Rate Limiter Tests ===
    // These tests verify the interval-based rate limiting behavior that was
    // causing warp to fail (tryWorking returning null on most ticks)

    @Test
    void tryWorking_ShouldNotDoWork_WhenIntervalNotReached() {
        // Create a TestWorldInteraction with interval=10 (requires 10 ticks between actions)
        TestWorldInteraction wi = withInterval(10);

        // First call increments ticksSinceLastAction to 1, which is < 10
        WorkOutput<Boolean, WorkPosition<Position>> result = wi.tryWorking(null, arbitrarySpot());

        // Should return a WorkOutput with didWork=false because interval not reached
        // (returns WorkOutput instead of null because canClaim is true in test)
        Assertions.assertNotNull(result, "tryWorking should return WorkOutput when canClaim is true");
        Assertions.assertFalse(result.worked(), "tryWorking should not do work when interval not reached");
        Assertions.assertEquals(1, wi.getTicksSinceLastAction());
        Assertions.assertFalse(wi.extracted);
    }

    @Test
    void tryWorking_ShouldWork_WhenIntervalReached() {
        TestWorldInteraction wi = withInterval(5);

        // Call tryWorking 5 times to reach the interval
        for (int i = 0; i < 5; i++) {
            wi.tryWorking(null, arbitrarySpot());
        }

        // 5th call should have allowed work (ticksSinceLastAction reaches interval)
        Assertions.assertEquals(0, wi.getTicksSinceLastAction(), "Should reset to 0 after work performed");
    }

    @Test
    void injectTicks_ShouldBypassRateLimiter() {
        TestWorldInteraction wi = withInterval(100);

        // Inject enough ticks to bypass the rate limiter
        wi.injectTicks(100);

        // Now tryWorking should proceed (we're at interval threshold)
        WorkOutput<Boolean, WorkPosition<Position>> result = wi.tryWorking(null, arbitrarySpot());

        // After work, ticksSinceLastAction should reset to 0
        Assertions.assertEquals(0, wi.getTicksSinceLastAction(),
                "injectTicks should allow work to proceed, resetting counter");
    }

    @Test
    void tryWorking_ShouldBlockMultipleTimes_UntilIntervalReached() {
        TestWorldInteraction wi = withInterval(3);

        // Call 1: ticksSinceLastAction becomes 1 (< 3), returns WorkOutput with didWork=false
        WorkOutput<Boolean, WorkPosition<Position>> result1 = wi.tryWorking(null, arbitrarySpot());
        Assertions.assertFalse(result1.worked(), "Should not do work on call 1");
        Assertions.assertEquals(1, wi.getTicksSinceLastAction());

        // Call 2: ticksSinceLastAction becomes 2 (< 3), returns WorkOutput with didWork=false
        WorkOutput<Boolean, WorkPosition<Position>> result2 = wi.tryWorking(null, arbitrarySpot());
        Assertions.assertFalse(result2.worked(), "Should not do work on call 2");
        Assertions.assertEquals(2, wi.getTicksSinceLastAction());

        // Call 3: ticksSinceLastAction becomes 3 (>= 3), work proceeds
        WorkOutput<Boolean, WorkPosition<Position>> result3 = wi.tryWorking(null, arbitrarySpot());
        Assertions.assertTrue(result3.worked(), "Should do work on call 3 when interval reached");
        Assertions.assertEquals(0, wi.getTicksSinceLastAction(), "Counter should reset after work");
    }

    @Test
    void injectTicks_ShouldAllowImmediateWork_WhenCalledBeforeTryWorking() {
        // This is the pattern used during warp: inject ticks BEFORE each tryWorking call
        TestWorldInteraction wi = withInterval(50);

        // Inject interval ticks before calling tryWorking (simulates warp behavior)
        wi.injectTicks(wi.getInterval());

        // Now tryWorking should proceed immediately
        wi.tryWorking(null, arbitrarySpot());

        Assertions.assertEquals(0, wi.getTicksSinceLastAction(),
                "Work should proceed and reset counter when ticks injected before call");
    }

    // === postExtractHook Null Handling Tests ===
    // These tests verify that when postExtractHook returns null, items are still
    // properly accumulated in the villager's inventory. The bug was:
    //   ts = postExtractHook(inputs, unit);  // returns null
    //   ts = setHeldItem(inputs, ts, ...);   // ts is null, falls back to original state
    // This caused only the last item to be kept during multi-item extraction.

    @Test
    void tryGiveItems_ShouldPassNonNullTownToSetHeldItem_WhenExtractingMultipleItems() {
        // This test verifies the fix for the postExtractHook null handling bug.
        // When extracting multiple items, setHeldItem should receive non-null town
        // state on all calls after the first (because the first call's result should
        // be passed to subsequent calls).
        //
        // Before the fix: all calls to setHeldItem had null town (ts reset by null hookResult)
        // After the fix: only first call has null town (ts preserved when hookResult is null)

        ArrayList<GathererJournalTest.TestItem> inventoryList = new ArrayList<>();
        inventoryList.add(new GathererJournalTest.TestItem("")); // Empty slot 1
        inventoryList.add(new GathererJournalTest.TestItem("")); // Empty slot 2
        inventoryList.add(new GathererJournalTest.TestItem("")); // Empty slot 3

        InventoryHandle<GathererJournalTest.TestItem> inventoryHandle = new InventoryHandle<>() {
            @Override
            public Collection<GathererJournalTest.TestItem> getItems() {
                return inventoryList;
            }

            @Override
            public void set(int ii, GathererJournalTest.TestItem item) {
                inventoryList.set(ii, item);
            }
        };

        ImmutableWorkStateContainer<Position, Boolean> statuses = testWorkStateContainer();

        // Create a TestWorldInteraction that extracts 3 items
        TestWorldInteraction wi = new TestWorldInteraction(
                0,
                ImmutableMap.of(),
                ImmutableMap.of(),
                ImmutableMap.of(),
                ImmutableMap.of(),
                ImmutableMap.of(),
                ImmutableList.of(
                        new GathererJournalTest.TestItem("bowl"),
                        new GathererJournalTest.TestItem("bowl"),
                        new GathererJournalTest.TestItem("bowl")
                ),
                ValidatedInventoryHandle.unvalidated(inventoryHandle),
                statuses,
                () -> new Claim(UUID.randomUUID(), 100)
        );

        // Set up job block state to be at extraction point (maxState with no work left)
        wi.setJobBlockState(null, true, arbitrarySpot().jobBlock(), State.fresh());

        // Reset tracking
        wi.resetSetHeldItemTracking();

        // Trigger extraction by calling tryWorking
        wi.tryWorking(null, arbitrarySpot());

        // Verify: When extracting 3 items, setHeldItem should be called 3 times.
        // With the fix, only the first call should have null town (because getTown returns null).
        // Calls 2 and 3 should have non-null town (the result from previous setHeldItem).
        //
        // Before the fix, ALL 3 calls would have null town because postExtractHook
        // returning null would reset ts to null before each setHeldItem call.

        Assertions.assertEquals(3, wi.getSetHeldItemCallsTotal(),
                "Should call setHeldItem 3 times for 3 items");

        // The key assertion: at most 1 call should have null town (the first one)
        // If more than 1 call has null town, the bug exists.
        Assertions.assertTrue(wi.getSetHeldItemCallsWithNullTown() <= 1,
                "At most 1 setHeldItem call should have null town (the first one). " +
                        "Got " + wi.getSetHeldItemCallsWithNullTown() + " null calls. " +
                        "This indicates the postExtractHook null handling bug.");
    }

    private TestWorldInteraction withInterval(int interval) {
        ImmutableWorkStateContainer<Position, Boolean> statuses = testWorkStateContainer();
        InventoryHandle<GathererJournalTest.TestItem> inventoryHandle = new InventoryHandle<>() {
            @Override
            public Collection<GathererJournalTest.TestItem> getItems() {
                return ImmutableList.of(new GathererJournalTest.TestItem(""));
            }

            @Override
            public void set(int ii, GathererJournalTest.TestItem shrink) {
            }
        };

        return new TestWorldInteraction(
                0, // maxState
                ImmutableMap.of(),
                ImmutableMap.of(),
                ImmutableMap.of(),
                ImmutableMap.of(),
                ImmutableMap.of(),
                ValidatedInventoryHandle.unvalidated(inventoryHandle),
                statuses,
                () -> new Claim(UUID.randomUUID(), 100),
                interval
        );
    }



    public static TestWorldInteraction noMemoryInventory(
            int i,
            ImmutableMap<Integer, MonoPredicateCollection<GathererJournalTest.TestItem>> toolsNeeded,
            ImmutableMap<Integer, Integer> workRequired,
            ImmutableMap<Integer, MonoPredicateCollection<GathererJournalTest.TestItem>> ingredients,
            Supplier<Collection<GathererJournalTest.TestItem>> inventory,
            Runnable onInventoryChange
    ) {
        ImmutableMap.Builder<Integer, Integer> alwaysOneBuilder = ImmutableMap.builder();
        ingredients.forEach((k, v) -> alwaysOneBuilder.put(k, 1));

        ImmutableMap.Builder<Integer, Integer> alwaysZeroBuilder = ImmutableMap.builder();
        ingredients.forEach((k, v) -> alwaysZeroBuilder.put(k, 0));


        InventoryHandle<GathererJournalTest.TestItem> inventoryHandle = new InventoryHandle<GathererJournalTest.TestItem>() {
            @Override
            public Collection<GathererJournalTest.TestItem> getItems() {
                return inventory.get();
            }

            @Override
            public void set(
                    int ii,
                    GathererJournalTest.TestItem shrink
            ) {
                onInventoryChange.run();
            }
        };

        ImmutableWorkStateContainer<Position, Boolean> statuses = AbstractWorldInteractionTest.testWorkStateContainer();
        //noinspection deprecation
        return new TestWorldInteraction(
                i, toolsNeeded, workRequired, ingredients,
                alwaysOneBuilder.build(), alwaysZeroBuilder.build(),
                ValidatedInventoryHandle.unvalidated(inventoryHandle), statuses,
                () -> new Claim(UUID.randomUUID(), 100)
        );
    }

    private static MonoPredicateCollection<GathererJournalTest.TestItem> exact(String name) {
        return new MonoPredicateCollection<>(new IPredicateCollection<>() {
            @Override
            public boolean isEmpty() {
                return false;
            }

            @Override
            public boolean test(GathererJournalTest.TestItem testItem) {
                return name.equals(testItem.value);
            }
        }, "exact");
    }


    @NotNull
    private static ImmutableWorkStateContainer<Position, Boolean> testWorkStateContainer() {
        HashMap<Position, State> ztate = new HashMap<>();
        return new ImmutableWorkStateContainer<>() {
            @Override
            public @Nullable State getJobBlockState(Position bp) {
                return ztate.get(bp);
            }

            @Override
            public ImmutableMap<Position, State> getAll() {
                return ImmutableMap.copyOf(ztate);
            }

            @Override
            public Boolean setJobBlockState(
                    Position bp,
                    State bs
            ) {
                ztate.put(bp, bs);
                return true;
            }

            @Override
            public Boolean setJobBlockStateWithTimer(
                    Position bp,
                    State bs,
                    int ticksToNextState
            ) {
                ztate.put(bp, bs); // Ignoring time
                return true;
            }

            @Override
            public Boolean clearState(Position bp) {
                ztate.remove(bp);
                return true;
            }

            @Override
            public boolean claimSpot(
                    Position bp,
                    Claim claim
            ) {
                return true;
            }

            @Override
            public void clearClaim(Position position) {

            }

            @Override
            public boolean canClaim(
                    Position position,
                    Supplier<Claim> makeClaim
            ) {
                return true;
            }

        };
    }

    @Test
    void Test_ShouldExtractForCompletelyEmptyWork() {
        AtomicBoolean inserted = new AtomicBoolean(false);
        TestWorldInteraction wi = noMemoryInventory(
                0, // max state (only one state here)
                ImmutableMap.of(),
                ImmutableMap.of(),
                ImmutableMap.of(),
                () -> ImmutableList.of(new GathererJournalTest.TestItem("")),
                () -> inserted.set(true)
        );

        State state = wi.getJobBlockState(null, arbitrarySpot(0).jobBlock());
        Assertions.assertNull(state);

        wi.tryWorking(null, arbitrarySpot(0)); // Try doing work
        wi.tryWorking(null, arbitrarySpot(0)); // Run once more to extract and reset state

        state = wi.getJobBlockState(null, arbitrarySpot(0).jobBlock());
        Assertions.assertFalse(inserted.get());
        Assertions.assertTrue(wi.extracted); // Extracted
        Assertions.assertEquals(State.fresh(), state);
    }

    @Test
    void Test_ShouldInsertForWorklessToollessJob() {
        AtomicBoolean inserted = new AtomicBoolean(false);
        TestWorldInteraction wi = noMemoryInventory(
                0, // max state (only one state here)
                ImmutableMap.of(), // No tools required
                ImmutableMap.of(), // No work required
                ImmutableMap.of(
                        0, exact("grapes") // Grapes required
                ),
                () -> ImmutableList.of(new GathererJournalTest.TestItem("grapes")),
                () -> inserted.set(true)
        );
        State state = wi.getJobBlockState(null, arbitrarySpot(0).jobBlock());
        Assertions.assertNull(state);

        wi.tryWorking(null, arbitrarySpot(0));

        state = wi.getJobBlockState(null, arbitrarySpot(0).jobBlock());
        Assertions.assertFalse(wi.extracted);

        Assertions.assertEquals(State.fresh().incrProcessing(), state);
        Assertions.assertTrue(inserted.get());
    }

    @Test
    void Test_ShouldInsertFirst_IfBlockRequiresIngredientsAndWork() {
        AtomicBoolean inserted = new AtomicBoolean(false);
        TestWorldInteraction wi = noMemoryInventory(
                0, // max state (only one state here)
                ImmutableMap.of(), // No tools required
                ImmutableMap.of(
                        0, 100 // 100 work required
                ),
                ImmutableMap.of(
                        0, exact("grapes") // Grapes required
                ),
                () -> ImmutableList.of(new GathererJournalTest.TestItem("grapes")),
                () -> inserted.set(true)
        );
        State state = wi.getJobBlockState(null, arbitrarySpot(0).jobBlock());
        Assertions.assertNull(state);
        Assertions.assertFalse(wi.extracted);
        Assertions.assertFalse(inserted.get());

        wi.tryWorking(null, arbitrarySpot(0));

        state = wi.getJobBlockState(null, arbitrarySpot(0).jobBlock());
        Assertions.assertNotNull(state);
        Assertions.assertEquals(0, state.processingState());
        Assertions.assertEquals(100, state.workLeft());
        Assertions.assertEquals(1, state.ingredientCount());
        Assertions.assertFalse(wi.extracted);
        Assertions.assertTrue(inserted.get());
    }

    @Test
    void Test_ShouldInsertThenProcessForToollessJob() {
        TestWorldInteraction wi = noMemoryInventory(
                0, // max state (only one state here)
                ImmutableMap.of(), // No tools required
                ImmutableMap.of(
                        0, 100 // 100 work required
                ),
                ImmutableMap.of(
                        0, exact("grapes") // Grapes required
                ),
                () -> ImmutableList.of(new GathererJournalTest.TestItem("grapes")),
                () -> {
                }
        );
        wi.tryWorking(null, arbitrarySpot(0)); // Insert (see test above)
        wi.tryWorking(null, arbitrarySpot(0)); // Process
        State state = wi.getJobBlockState(null, arbitrarySpot(0).jobBlock());
        Assertions.assertNotNull(state);
        Assertions.assertEquals(0, state.processingState());
        Assertions.assertEquals(99, state.workLeft());
        Assertions.assertEquals(1, state.ingredientCount());
        Assertions.assertFalse(wi.extracted);
    }

    @Test
    void Test_ShouldInsertThenProcessThenExtractForToollessJob() {
        TestWorldInteraction wi = noMemoryInventory(
                0, // max state (only one state here)
                ImmutableMap.of(), // No tools required
                ImmutableMap.of(
                        0, 1 // 1 work required
                ),
                ImmutableMap.of(
                        0, exact("apples") // Grapes required
                ),
                () -> ImmutableList.of(new GathererJournalTest.TestItem("apples")),
                () -> {
                }
        );
        wi.tryWorking(null, arbitrarySpot(0)); // Insert (see test above)

        wi.tryWorking(null, arbitrarySpot(0)); // Process (see test above)

        wi.tryWorking(null, arbitrarySpot(0)); // Extract
        @Nullable State state = wi.getJobBlockState(null, arbitrarySpot(0).jobBlock());
        Assertions.assertEquals(State.fresh(), state);
        Assertions.assertTrue(wi.extracted);
    }

    @Test
    void Test_ShouldInsertAndNotProcess_ForJobWithTwoStages_OnFirstTry() {
        AtomicBoolean inserted = new AtomicBoolean(false);
        TestWorldInteraction wi = noMemoryInventory(
                3,
                ImmutableMap.of(), // No tools required
                ImmutableMap.of(
                        0, 0, // No work required at stage 0
                        1, 100 // 100 work required
                ),
                ImmutableMap.of(
                        0, exact("grapes") // Grapes required at stage 0
                ),
                () -> ImmutableList.of(new GathererJournalTest.TestItem("grapes")),
                () -> inserted.set(true)
        );
        State state = wi.getJobBlockState(null, arbitrarySpot(0).jobBlock());
        Assertions.assertNull(state);

        wi.tryWorking(null, arbitrarySpot(0));

        Assertions.assertFalse(wi.extracted);

        state = wi.getJobBlockState(null, arbitrarySpot(0).jobBlock());
        Assertions.assertNotNull(state);
        Assertions.assertEquals(1, state.processingState());
        Assertions.assertEquals(100, state.workLeft());
        Assertions.assertEquals(0, state.ingredientCount());

        Assertions.assertTrue(inserted.get());
    }

    @Test
    void Test_ShouldMoveToStage2_AfterFirstTry() {
        final AtomicBoolean inserted = new AtomicBoolean(false);
        TestWorldInteraction wi = noMemoryInventory(
                3,
                ImmutableMap.of(), // No tools required
                ImmutableMap.of(
                        0, 0, // No work required at stage 0
                        1, 100 // 100 work required
                ),
                ImmutableMap.of(
                        0, exact("grapes") // Grapes required at stage 0
                ),
                () -> ImmutableList.of(new GathererJournalTest.TestItem("grapes")),
                () -> inserted.set(true)
        );
        WorkPosition<Position> spot = arbitrarySpot(0);

        Assertions.assertNull(wi.getJobBlockState(null, spot.jobBlock()));

        wi.tryWorking(null, spot);

        State state = wi.getJobBlockState(null, spot.jobBlock());
        Assertions.assertNotNull(state);
        Assertions.assertEquals(1, state.processingState());
        Assertions.assertEquals(100, state.workLeft()); // Not processed
        Assertions.assertFalse(wi.extracted);
        Assertions.assertTrue(inserted.get());
    }


    @Test
    void Test_ShouldInsertAndProcess_ForJobWithTwoStages_AfterSecondTry() {
        AtomicBoolean inserted = new AtomicBoolean(false);
        TestWorldInteraction wi = noMemoryInventory(
                2,
                ImmutableMap.of(), // No tools required
                ImmutableMap.of(
                        0, 0, // No work required at stage 0
                        1, 100 // 100 work required
                ),
                ImmutableMap.of(
                        0, exact("grapes") // Grapes required at stage 0
                ),
                () -> ImmutableList.of(new GathererJournalTest.TestItem("grapes")),
                () -> inserted.set(true)
        );

        wi.tryWorking(null, arbitrarySpot(0));
        wi.tryWorking(null, arbitrarySpot(1));

        State state = wi.getJobBlockState(null, arbitrarySpot(1).jobBlock());
        Assertions.assertNotNull(state);
        Assertions.assertFalse(wi.extracted);
        Assertions.assertEquals(1, state.processingState());
        Assertions.assertEquals(99, state.workLeft()); // Processed
        Assertions.assertTrue(inserted.get()); // Inserted
    }

    @Test
    void Test_ShouldInsertAndProcessAndExtract_ForJobWithThreeStages_AfterThirdTry() {
        AtomicBoolean inserted = new AtomicBoolean(false);
        TestWorldInteraction wi = noMemoryInventory(
                2,
                ImmutableMap.of(), // No tools required
                ImmutableMap.of(
                        0, 0, // No work required at stage 0
                        1, 1, // 1 work required at stage 1
                        2, 0 // No work required at stage 2
                ),
                ImmutableMap.of(
                        0, exact("grapes") // Grapes required at stage 0
                ),
                () -> ImmutableList.of(new GathererJournalTest.TestItem("grapes")),
                () -> inserted.set(true)
        );

        wi.tryWorking(null, arbitrarySpot(0));
        wi.tryWorking(null, arbitrarySpot(1));
        wi.tryWorking(null, arbitrarySpot(2));

        State state = wi.getJobBlockState(null, arbitrarySpot(0).jobBlock());
        Assertions.assertEquals(State.fresh(), state);
        Assertions.assertTrue(inserted.get()); // Inserted
        Assertions.assertTrue(wi.extracted); // Extracted
    }

    @Test
    void Test_ShouldDoNothingIfToolIsRequiredButNotHad() {
        AtomicBoolean inserted = new AtomicBoolean(false);
        TestWorldInteraction wi = noMemoryInventory(
                2,
                ImmutableMap.of(
                        0, alwaysFalse // Villager does not have the needed tool
                ),
                ImmutableMap.of(
                        0, 1 // Work required at stage 0
                ),
                ImmutableMap.of(
                        0, alwaysTrue // Villager has all the items needed
                ),
                () -> ImmutableList.of(new GathererJournalTest.TestItem("")),
                () -> inserted.set(true)
        );

        wi.tryWorking(null, arbitrarySpot(0));

        State state = wi.getJobBlockState(null, arbitrarySpot(0).jobBlock());
        Assertions.assertNull(state); // Not processed
        Assertions.assertFalse(inserted.get()); // Not inserted
        Assertions.assertFalse(wi.extracted); // Not extracted
    }

    @Test
    void Test_ShouldAdvanceProgress_IfNoIngredientsAreNeeded_AndToolIsHad() {
        AtomicBoolean inserted = new AtomicBoolean(false);
        TestWorldInteraction wi = noMemoryInventory(
                2,
                ImmutableMap.of(
                        0, alwaysTrue // Villager has the needed tool
                ),
                ImmutableMap.of(
                        // No work required
                ),
                ImmutableMap.of(
                        // No items required
                ),
                // This is not actually important because we've overloaded the item check, above
                () -> ImmutableList.of(new GathererJournalTest.TestItem("axe")),
                () -> inserted.set(true)
        );

        wi.tryWorking(null, arbitrarySpot(0));

        Assertions.assertFalse(inserted.get()); // Not inserted
        Assertions.assertFalse(wi.extracted); // Not extracted
        State state = wi.getJobBlockState(null, arbitrarySpot(0).jobBlock());
        Assertions.assertNotNull(state);
        Assertions.assertEquals(0, state.workLeft());
        Assertions.assertEquals(0, state.ingredientCount());
        Assertions.assertEquals(1, state.processingState());
    }

    @Test
    void Test_ShouldInsertAndProcessAndExtract_WhenToolsRequiredAndPossessed_ForJobWithThreeStages_AfterThirdTry() {
        AtomicBoolean inserted = new AtomicBoolean(false);
        TestWorldInteraction wi = noMemoryInventory(
                2,
                ImmutableMap.of(
                        0, alwaysTrue, // Villager has the needed tools
                        1, alwaysTrue, // Villager has the needed tools
                        2, alwaysTrue // Villager has the needed tools
                ),
                ImmutableMap.of(
                        0, 0, // No work required at stage 0
                        1, 1, // 1 work required at stage 1
                        2, 0 // No work required at stage 2
                ),
                ImmutableMap.of(
                        0, exact("grapes") // Grapes required at stage 0
                ),
                () -> ImmutableList.of(new GathererJournalTest.TestItem("grapes")),
                () -> inserted.set(true)
        );

        wi.tryWorking(null, arbitrarySpot(0));
        wi.tryWorking(null, arbitrarySpot(1));
        wi.tryWorking(null, arbitrarySpot(2));

        State state = wi.getJobBlockState(null, arbitrarySpot(0).jobBlock());
        Assertions.assertEquals(State.fresh(), state);
        Assertions.assertTrue(inserted.get()); // Inserted
        Assertions.assertTrue(wi.extracted); // Extracted
    }

    @Test
    void Test_ShouldNotSetTimerIfToolIsRequired() throws ItemCountMismatch {
        AtomicBoolean inserted = new AtomicBoolean(false);
        TestWorldInteraction wi = new TestWorldInteraction(
                2,
                ImmutableMap.of(
                        1, alwaysFalse // Villager does not have the tool
                ),
                ImmutableMap.of(
                        // No work required
                ),
                ImmutableMap.of(
                        0, exact("grapes") // Grapes required at stage 0
                ),
                ImmutableMap.of(
                        0, 1
                ),
                ImmutableMap.of(
                        0, 0, // No timer at stage 0
                        1, 100 // Timer applies to stage 1
                ),
                new ValidatedInventoryHandle<>(new InventoryHandle<GathererJournalTest.TestItem>() {
                    @Override
                    public Collection<GathererJournalTest.TestItem> getItems() {
                        return ImmutableList.of(new GathererJournalTest.TestItem("grapes"));
                    }

                    @Override
                    public void set(
                            int ii,
                            GathererJournalTest.TestItem shrink
                    ) {
                        inserted.set(true);
                    }
                }, 1),
                testWorkStateContainer(),
                () -> new Claim(UUID.randomUUID(), 100)
        );

        wi.tryWorking(null, arbitrarySpot(0));

        State state = wi.getJobBlockState(null, arbitrarySpot(0).jobBlock());
        Assertions.assertFalse(wi.extracted); // Not Extracted
        Assertions.assertTrue(inserted.get()); // Inserted
        Assertions.assertNotNull(state);
        Assertions.assertEquals(State.fresh().incrProcessing(), state);
    }

    /**
     * @deprecated State does nothing, use the version with no args.
     */
    @NotNull
    @Deprecated
    private static WorkPosition<Position> arbitrarySpot(int state) {
        return new WorkPosition<>(new Position(1, 2), new Position(0, 1));
    }

    private static WorkPosition<Position> arbitrarySpot() {
        return arbitrarySpot(0);
    }

    @Test
    void tryWorking_ShouldInsertIngredient_WhenToolsRequiredAtEarlierState_ButOnlyIngredientsRequiredNow() throws ItemCountMismatch {
        AtomicBoolean inserted = new AtomicBoolean(false);
        TestWorldInteraction wi = new TestWorldInteraction(
                3,
                ImmutableMap.of(
                        0, alwaysTrue,
                        1, alwaysTrue,
                        2, new MonoPredicateCollection<>(
                                MonoPredicateCollection.empty("no tools required"),
                                "no tools required"
                        )
                ),
                ImmutableMap.of(
                        1, 10 // Work required in a previous state
                ),
                ImmutableMap.of(
                        2, exact("grapes") // Grapes required at stage 2
                ),
                ImmutableMap.of(
                        2, 1
                ),
                ImmutableMap.of(
                        // No timers
                ),
                new ValidatedInventoryHandle<>(new InventoryHandle<GathererJournalTest.TestItem>() {
                    @Override
                    public Collection<GathererJournalTest.TestItem> getItems() {
                        return ImmutableList.of(new GathererJournalTest.TestItem("grapes"));
                    }

                    @Override
                    public void set(
                            int ii,
                            GathererJournalTest.TestItem shrink
                    ) {
                        inserted.set(true);
                    }
                }, 1),
                testWorkStateContainer(),
                () -> new Claim(UUID.randomUUID(), 100)
        );

        wi.setJobBlockState(null, true, arbitrarySpot().jobBlock(), State.freshAtState(2));
        wi.tryWorking(null, arbitrarySpot());
        Assertions.assertTrue(inserted.get());
    }

}

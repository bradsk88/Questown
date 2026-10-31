package ca.bradj.questown.jobs;

import ca.bradj.questown.jobs.declarative.ContainersClean;
import ca.bradj.questown.jobs.declarative.WithReason;
import ca.bradj.questown.jobs.leaver.ContainerTarget;
import ca.bradj.questown.jobs.leaver.RankBoost;
import ca.bradj.questown.logic.IPredicateCollection;
import ca.bradj.questown.logic.PredicateCollection;
import ca.bradj.roomrecipes.core.space.Position;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 2026-10-14 playtest: hunter's mat block sat at its terminal (extractable) state; the
 * hunter had lost its sword (it was deposited into the village chest on a drop-loot).
 * With no state at a workable block, {@code TownHasSupplies} collected NO relevant
 * tools/ingredients, so "town has supplies" was false even though the village chest
 * held a stone_sword — and the "get work supplies" branch of the status circuit was
 * dead. The hunter was soft-locked in NO_JOBSITE until the world was reset.
 *
 * <p>The requirement set must fall back to the full set when no state is currently
 * workable, so the town can still acquire the tool needed to resume the cycle.
 */
class TownHasSuppliesNoWorkableStateTest {

    record FakeItem(String name) implements Item<FakeItem> {
        @Override
        public boolean isEmpty() {
            return name.equals("air");
        }

        @Override
        public boolean isFood() {
            return false;
        }

        @Override
        public FakeItem shrink() {
            return this;
        }

        @Override
        public String getShortName() {
            return name;
        }

        @Override
        public FakeItem unit() {
            return this;
        }

        @Override
        public int quantity() {
            return 1;
        }
    }

    static final class FakeContainer implements ContainerTarget.Container<FakeItem> {
        private final FakeItem[] slots;

        FakeContainer(FakeItem... slots) {
            this.slots = slots;
        }

        @Override
        public int size() {
            return slots.length;
        }

        @Override
        public FakeItem getItem(int i) {
            return slots[i];
        }

        @Override
        public FakeItem removeItem(int index) {
            FakeItem out = slots[index];
            slots[index] = new FakeItem("air");
            return out;
        }

        @Override
        public boolean setItem(int index, FakeItem item) {
            slots[index] = item;
            return true;
        }

        @Override
        public boolean isFull() {
            return false;
        }

        @Override
        public String toShortString() {
            return "chest";
        }

        @Override
        public String toShortString(boolean includeAir) {
            return "chest";
        }

        @Override
        public boolean canAcceptIfSpaceAllows(FakeItem item) {
            return !item.isEmpty();
        }

        @Override
        public RankBoost getItemAcceptanceRankBoost() {
            return RankBoost.SAME_AS_VANILLA_CHEST;
        }
    }

    /** No spot in any state is workable — mirrors the mat sitting at its terminal state. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static RoomsWithWorkableStatefulBlocks<?> notWorkable(int state) {
        Rooms rooms = new Rooms(ImmutableMap.of(), ImmutableMap.of(), ImmutableMap.of());
        return new RoomsWithWorkableStatefulBlocks(state, () -> rooms, String::valueOf);
    }

    @SuppressWarnings("unchecked")
    private static RoomsWithWorkableStatefulBlocks<Position> workableState0() {
        Rooms<Position, ca.bradj.roomrecipes.core.Room> r = new Rooms<>(
                ImmutableMap.of(new Position(-4, 280), 0),
                ImmutableMap.of(),
                ImmutableMap.of(new Position(-4, 280), true)
        );
        return new RoomsWithWorkableStatefulBlocks<>(0, () -> r, p -> p.toString());
    }

    private static <T> PredicateCollection<T, T> matches(String name, String description) {
        IPredicateCollection<T> set = new IPredicateCollection<>() {
            @Override
            public boolean isEmpty() {
                return false;
            }

            @Override
            public boolean test(Object o) {
                return o instanceof Item<?> i && i.getShortName().equals(name);
            }
        };
        return PredicateCollection.wrap(
                set,
                IPredicateCollection::isEmpty,
                (pc, t) -> t != null && ((Item<?>) t).getShortName().equals(name),
                description
        );
    }

    private static ContainerTarget<ContainerTarget.Container<FakeItem>, FakeItem> chest(FakeItem... items) {
        Position p = new Position(3, 286);
        return new ContainerTarget<>(
                p, 67, p,
                new FakeContainer(items),
                () -> true,
                i -> {
                },
                null,
                0f
        );
    }

    private static ContainersClean.JobSite<ContainerTarget<ContainerTarget.Container<FakeItem>, FakeItem>>
    siteHolding(ContainerTarget<ContainerTarget.Container<FakeItem>, FakeItem> target) {
        return new ContainersClean.JobSite<>() {
            @Override
            public ImmutableList<ContainersClean.Block<ContainerTarget<ContainerTarget.Container<FakeItem>, FakeItem>>> getBlocks() {
                return ImmutableList.of(new ContainersClean.Block<>() {
                    @Override
                    public boolean isAir() {
                        return false;
                    }

                    @Override
                    public boolean isJobBlock() {
                        return false;
                    }

                    @Override
                    public @Nullable ContainerTarget<ContainerTarget.Container<FakeItem>, FakeItem> asContainer() {
                        return null;
                    }

                    @Override
                    public @Nullable ContainerTarget<ContainerTarget.Container<FakeItem>, FakeItem> asChest() {
                        return target;
                    }
                });
            }

            @Override
            public boolean isJobSite() {
                return false;
            }
        };
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static TownHasSupplies<FakeItem, FakeItem, ContainerTarget<ContainerTarget.Container<FakeItem>, FakeItem>>
    hasSupplies(boolean state0Workable) {
        RoomsWithWorkableStatefulBlocks<?> g0 = state0Workable ? workableState0() : notWorkable(0);
        ImmutableMap<Integer, RoomsWithWorkableStatefulBlocks<?>> states =
                ImmutableMap.of(0, g0, 1, notWorkable(1), 2, notWorkable(2));

        return new TownHasSupplies<>(
                s -> null,
                s -> s == 0 ? matches("stone_sword", "swords") : null,
                () -> ImmutableList.of(siteHolding(chest(new FakeItem("stone_sword"), new FakeItem("mutton")))),
                () -> states,
                Function.identity()
        );
    }

    @Test
    void tool_found_when_a_state_is_workable() {
        TownHasSupplies<FakeItem, FakeItem, ContainerTarget<ContainerTarget.Container<FakeItem>, FakeItem>> h =
                hasSupplies(true);
        WithReason<Boolean> r = h.apply(() -> null);
        assertTrue(r.value, "state 0 workable: the sword in the chest satisfies the tool need. Reason: " + r.reason);
    }

    @Test
    void tool_still_counts_when_no_state_is_workable() {
        // The mat sits at its terminal (extractable) state: no state has a workable block,
        // but the town still needs the tool to resume the cycle — the sword in the chest
        // must count.
        TownHasSupplies<FakeItem, FakeItem, ContainerTarget<ContainerTarget.Container<FakeItem>, FakeItem>> h =
                hasSupplies(false);
        WithReason<Boolean> r = h.apply(() -> null);
        assertTrue(r.value, "no workable state: the required tool must still count. Reason: " + r.reason);
    }

    @Test
    void tool_does_not_count_when_the_town_has_no_job_block_at_all() {
        // 2026-10-19 playtest: cook/fuel with every furnace already fuelled. No furnace passes the
        // empty-fuel-slot precondition, so there is no cycle to resume and the coal in the chest must
        // not make "town has supplies" true (the cook sat in COLLECTING_SUPPLIES instead of moving on).
        ImmutableMap<Integer, RoomsWithWorkableStatefulBlocks<?>> states =
                ImmutableMap.of(0, notWorkable(0), 1, notWorkable(1));
        @SuppressWarnings("unchecked")
        TownHasSupplies<FakeItem, FakeItem, ContainerTarget<ContainerTarget.Container<FakeItem>, FakeItem>> h =
                new TownHasSupplies<>(
                        s -> null,
                        s -> s == 0 ? matches("coal", "coals") : null,
                        () -> ImmutableList.of(siteHolding(chest(new FakeItem("coal")))),
                        () -> states,
                        Function.identity(),
                        () -> false
                );
        WithReason<Boolean> r = h.apply(() -> null);
        assertFalse(r.value, "no job block anywhere: nothing to collect supplies for. Reason: " + r.reason);
    }

    @Test
    void nothing_needed_when_no_state_workable_and_no_tools_declared() {
        // No tools, no ingredients, no workable state: the town genuinely has nothing to supply.
        ImmutableMap<Integer, RoomsWithWorkableStatefulBlocks<?>> states =
                ImmutableMap.of(0, notWorkable(0), 1, notWorkable(1), 2, notWorkable(2));
        @SuppressWarnings("unchecked")
        TownHasSupplies<FakeItem, FakeItem, ContainerTarget<ContainerTarget.Container<FakeItem>, FakeItem>> h =
                new TownHasSupplies<>(
                        s -> null,
                        s -> null,
                        () -> ImmutableList.of(siteHolding(chest(new FakeItem("mutton")))),
                        () -> states,
                        Function.identity()
                );
        WithReason<Boolean> r = h.apply(() -> null);
        assertFalse(r.value, "no requirements at all: no supplies. Reason: " + r.reason);
    }
}

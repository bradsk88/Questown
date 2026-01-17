package ca.bradj.questown.integration.minecraft;

import ca.bradj.questown.jobs.leaver.ContainerTarget;
import ca.bradj.questown.town.TownState;
import ca.bradj.questown.town.workstatus.State;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class MCTownState extends TownState<MCContainer, MCTownItem, MCHeldItem, BlockPos, MCTownState> {

    // TODO: Move to the base class?
    private final ArrayList<MCHeldItem> knowledge = new ArrayList<>();

    // Tracks items inserted by villagers during warp, for recovery if NO_SUPPLIES is encountered.
    // Key: villager index, Value: map of (workBlockPos -> list of inserted items)
    private final ImmutableMap<Integer, ImmutableMap<BlockPos, ImmutableList<MCHeldItem>>> insertedItems;

    public MCTownState(
            @NotNull List<VillagerData<MCHeldItem>> villagers,
            @NotNull List<ContainerTarget<MCContainer, MCTownItem>> containers,
            @NotNull ImmutableMap<BlockPos, State> workStates,
            @NotNull ImmutableMap<BlockPos, Integer> workTimers,
            @NotNull List<BlockPos> gates,
            @NotNull ImmutableList<MCHeldItem> knowledge,
            @NotNull ImmutableMap<UUID, Boolean> blocksOfProgress,
            long worldTimeAtSleep
    ) {
        this(villagers, containers, workStates, workTimers, gates, knowledge, blocksOfProgress, worldTimeAtSleep, ImmutableMap.of());
    }

    public MCTownState(
            @NotNull List<VillagerData<MCHeldItem>> villagers,
            @NotNull List<ContainerTarget<MCContainer, MCTownItem>> containers,
            @NotNull ImmutableMap<BlockPos, State> workStates,
            @NotNull ImmutableMap<BlockPos, Integer> workTimers,
            @NotNull List<BlockPos> gates,
            @NotNull ImmutableList<MCHeldItem> knowledge,
            @NotNull ImmutableMap<UUID, Boolean> blocksOfProgress,
            long worldTimeAtSleep,
            @NotNull ImmutableMap<Integer, ImmutableMap<BlockPos, ImmutableList<MCHeldItem>>> insertedItems
    ) {
        super(villagers, containers, workStates, workTimers, gates, blocksOfProgress, worldTimeAtSleep);
        this.knowledge.addAll(knowledge);
        this.insertedItems = insertedItems;
    }

    @Override
    protected MCTownState newTownState(
            ImmutableList<VillagerData<MCHeldItem>> villagers,
            ImmutableList<ContainerTarget<MCContainer, MCTownItem>> containers,
            ImmutableMap<BlockPos, State> workStates,
            ImmutableMap<BlockPos, Integer> workTimers,
            ImmutableList<BlockPos> gates,
            ImmutableMap<UUID, Boolean> blocksOfProgress,
            long worldTimeAtSleep
    ) {
        return new MCTownState(
                villagers,
                containers,
                workStates,
                workTimers,
                gates,
                ImmutableList.copyOf(knowledge),
                blocksOfProgress,
                worldTimeAtSleep,
                insertedItems
        );
    }

    /**
     * Records an item that was inserted into a work block by a villager during warp.
     * Used for item recovery if NO_SUPPLIES is encountered.
     */
    public MCTownState withInsertedItem(int villagerIndex, BlockPos workPos, MCHeldItem item) {
        java.util.Map<Integer, ImmutableMap<BlockPos, ImmutableList<MCHeldItem>>> newOuter = new java.util.HashMap<>(insertedItems);
        ImmutableMap<BlockPos, ImmutableList<MCHeldItem>> villagerItems = insertedItems.getOrDefault(villagerIndex, ImmutableMap.of());
        java.util.Map<BlockPos, ImmutableList<MCHeldItem>> newInner = new java.util.HashMap<>(villagerItems);
        ImmutableList<MCHeldItem> existing = villagerItems.getOrDefault(workPos, ImmutableList.of());
        newInner.put(workPos, ImmutableList.<MCHeldItem>builder().addAll(existing).add(item).build());
        newOuter.put(villagerIndex, ImmutableMap.copyOf(newInner));
        return new MCTownState(
                villagers,
                containers,
                workStates,
                workTimers,
                gates,
                ImmutableList.copyOf(knowledge),
                blocksOfProgress,
                worldTimeAtSleep,
                ImmutableMap.copyOf(newOuter)
        );
    }

    /**
     * Returns the number of items inserted by a villager at a specific work block.
     */
    public int getInsertedItemsCount(int villagerIndex, BlockPos workPos) {
        ImmutableMap<BlockPos, ImmutableList<MCHeldItem>> villagerItems = insertedItems.getOrDefault(villagerIndex, ImmutableMap.of());
        return villagerItems.getOrDefault(workPos, ImmutableList.of()).size();
    }

    /**
     * Returns the inserted items map (for state preservation during warp).
     */
    public ImmutableMap<Integer, ImmutableMap<BlockPos, ImmutableList<MCHeldItem>>> getInsertedItems() {
        return insertedItems;
    }

    /**
     * Returns all inserted items for a villager (across all work blocks) and creates a new state with them cleared.
     */
    public java.util.Map.Entry<MCTownState, ImmutableList<MCHeldItem>> withInsertedItemsCleared(int villagerIndex) {
        ImmutableMap<BlockPos, ImmutableList<MCHeldItem>> villagerItems = insertedItems.getOrDefault(villagerIndex, ImmutableMap.of());
        ImmutableList.Builder<MCHeldItem> allItems = ImmutableList.builder();
        villagerItems.values().forEach(allItems::addAll);

        java.util.Map<Integer, ImmutableMap<BlockPos, ImmutableList<MCHeldItem>>> newOuter = new java.util.HashMap<>(insertedItems);
        newOuter.remove(villagerIndex);

        MCTownState newState = new MCTownState(
                villagers,
                containers,
                workStates,
                workTimers,
                gates,
                ImmutableList.copyOf(knowledge),
                blocksOfProgress,
                worldTimeAtSleep,
                ImmutableMap.copyOf(newOuter)
        );
        return new java.util.AbstractMap.SimpleEntry<>(newState, allItems.build());
    }

    /**
     * Returns the total count of items inserted by a villager across all work blocks.
     */
    public int getTotalInsertedItemsCount(int villagerIndex) {
        ImmutableMap<BlockPos, ImmutableList<MCHeldItem>> villagerItems = insertedItems.getOrDefault(villagerIndex, ImmutableMap.of());
        return villagerItems.values().stream().mapToInt(ImmutableList::size).sum();
    }

    public MCTownState withKnowledge(MCHeldItem item) {
        MCTownState unchanged = unchanged();
        unchanged.knowledge.add(item);
        return unchanged;
    }

    public ImmutableList<MCHeldItem> knowledge() {
        return ImmutableList.copyOf(knowledge);
    }

    public MCTownState withHungerFilledBy(
            UUID uuid,
            Float up
    ) {
        // TODO[Warp]: Implement this
        return unchanged();
    }
}

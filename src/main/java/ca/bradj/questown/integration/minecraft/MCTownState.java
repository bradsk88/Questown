package ca.bradj.questown.integration.minecraft;

import ca.bradj.questown.jobs.leaver.ContainerTarget;
import ca.bradj.questown.town.TownState;
import ca.bradj.questown.town.workstatus.State;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

public class MCTownState extends TownState<MCContainer, MCTownItem, MCHeldItem, BlockPos, MCTownState> {

    // TODO: Move to the base class?
    private final ArrayList<MCHeldItem> knowledge = new ArrayList<>();

    // Tracks items inserted by villagers during warp, for recovery if NO_SUPPLIES is encountered.
    // Key: villager index, Value: map of (workBlockPos -> list of inserted items)
    private final ImmutableMap<Integer, ImmutableMap<BlockPos, ImmutableList<MCHeldItem>>> insertedItems;

    /** LOOT extractions recorded during a warp, applied once when the warp ends. Shared across state copies. */
    private final ArrayList<MCHeldItem> recordedLootExtractions;

    /** XP each villager earned during a warp, granted once when the warp ends. Shared across state copies. */
    private final HashMap<UUID, Integer> recordedExperience;

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
        this(villagers, containers, workStates, workTimers, gates, knowledge, blocksOfProgress, worldTimeAtSleep, ImmutableMap.of(), new ArrayList<>(), new HashMap<>());
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
            @NotNull ImmutableMap<Integer, ImmutableMap<BlockPos, ImmutableList<MCHeldItem>>> insertedItems,
            @NotNull ArrayList<MCHeldItem> recordedLootExtractions,
            @NotNull HashMap<UUID, Integer> recordedExperience
    ) {
        super(villagers, containers, workStates, workTimers, gates, blocksOfProgress, worldTimeAtSleep);
        this.knowledge.addAll(knowledge);
        this.insertedItems = insertedItems;
        this.recordedLootExtractions = recordedLootExtractions;
        this.recordedExperience = recordedExperience;
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
                insertedItems,
                recordedLootExtractions,
                recordedExperience
        );
    }

    public void recordLootExtract(MCHeldItem item) {
        recordedLootExtractions.add(item);
    }

    public ImmutableList<MCHeldItem> recordedLootExtractions() {
        return ImmutableList.copyOf(recordedLootExtractions);
    }

    /** Clears the recorded extractions once they have been applied. */
    public MCTownState clearRecordedLootExtractions() {
        recordedLootExtractions.clear();
        return this;
    }

    public void recordExperience(UUID villager, int xp) {
        if (xp <= 0) {
            return;
        }
        recordedExperience.merge(villager, xp, Integer::sum);
    }

    public ImmutableMap<UUID, Integer> recordedExperience() {
        return ImmutableMap.copyOf(recordedExperience);
    }

    /** Clears the recorded XP once it has been granted. */
    public MCTownState clearRecordedExperience() {
        recordedExperience.clear();
        return this;
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
                ImmutableMap.copyOf(newOuter),
                recordedLootExtractions,
                recordedExperience
        );
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
                ImmutableMap.copyOf(newOuter),
                recordedLootExtractions,
                recordedExperience
        );
        return new java.util.AbstractMap.SimpleEntry<>(newState, allItems.build());
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

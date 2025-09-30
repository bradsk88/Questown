package ca.bradj.questown.jobs;

import ca.bradj.questown.integration.minecraft.MCContainer;
import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.integration.minecraft.MCTownItem;
import ca.bradj.questown.integration.minecraft.MCTownState;
import ca.bradj.questown.items.EffectMetaItem;
import ca.bradj.questown.jobs.declarative.*;
import ca.bradj.questown.jobs.leaver.ContainerTarget;
import ca.bradj.questown.jobs.production.ProductionStatus;
import ca.bradj.questown.jobs.production.RoomsNeedingVillagerInput;
import ca.bradj.questown.jobs.production.RoomsNeedingVillagerInput.NVIRoom;
import ca.bradj.questown.logic.PredicateCollection;
import ca.bradj.questown.mc.Compat;
import ca.bradj.questown.mc.Util;
import ca.bradj.questown.town.Claim;
import ca.bradj.questown.town.Effect;
import ca.bradj.questown.town.TownState;
import ca.bradj.questown.town.TownVillagerMoods;
import ca.bradj.questown.town.interfaces.ImmutableWorkStateContainer;
import ca.bradj.questown.town.workstatus.State;
import ca.bradj.roomrecipes.adapter.RoomRecipeMatch;
import ca.bradj.roomrecipes.serialization.MCRoom;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

public class MCTownStateWorldInteraction extends
        AbstractWorldInteraction<MCTownStateWorldInteraction.Inputs, BlockPos, MCTownItem, MCHeldItem, MCTownState> {

    private final BlockPos townPos;

    public record Inputs(MCTownState town, ServerLevel level, UUID vUUID) {
    }

    private final BiFunction<ServerLevel, Collection<MCHeldItem>, Iterable<MCHeldItem>> resultGenerator;

    public MCTownStateWorldInteraction(
            BlockPos townPos,
            JobID jobId,
            int villagerIndex,
            int interval,
            int maxState,
            DeclarativeJobChecks<Inputs, MCHeldItem, MCTownItem, RoomRecipeMatch<MCRoom>, BlockPos> checks,
            BiFunction<ServerLevel, Collection<MCHeldItem>, Iterable<MCHeldItem>> resultGenerator,
            Function<MCTownStateWorldInteraction.Inputs, Claim> claimSpots,
            Map<ProductionStatus, Collection<String>> specialRules
    ) {
        super(jobId, villagerIndex, interval, maxState, checks, claimSpots, specialRules);
        this.resultGenerator = resultGenerator;
        this.townPos = townPos;
    }

    @Override
    protected int getWorkSpeedOf10(Inputs inputs) {
        Collection<Effect> effects = inputs.town().getVillager(villagerIndex)
                                           .getEffectsAndClearExpired(Util.getTick(inputs.level()));
        return Math.max(TownVillagerMoods.compute(effects) / 10, 1);
    }

    @Override
    protected int getAffectedTime(
            Inputs inputs,
            Integer nextStepTime
    ) {
        return (int) (getTimeFactor(inputs) * nextStepTime);
    }

    private float getTimeFactor(Inputs inputs) {
        Collection<Effect> effects = inputs.town().getVillager(villagerIndex)
                                           .getEffectsAndClearExpired(Util.getTick(inputs.level()));
        return WorkEffects.calculateTimeFactor(effects);
    }

    @Override
    protected MCTownState setHeldItem(
            Inputs uxtra,
            MCTownState tuwn,
            int villagerIndex,
            int itemIndex,
            MCHeldItem item
    ) {
        if (tuwn == null) {
            tuwn = uxtra.town();
        }
        TownState.VillagerData<MCHeldItem> villager = tuwn.villagers.get(villagerIndex);
        return tuwn.withVillagerData(villagerIndex, villager.withSetItem(itemIndex, item));
    }

    @Override
    protected MCTownState degradeTool(
            Inputs mcTownState,
            @Nullable MCTownState tuwn,
            PredicateCollection<MCTownItem, ?> isExpectedTool
    ) {
        if (tuwn == null) {
            tuwn = mcTownState.town();
        }

        int i = -1;
        for (MCHeldItem item : this.getHeldItems(mcTownState, villagerIndex)) {
            i++;
            if (!isExpectedTool.test(item.get())) {
                continue;
            }
            ItemStack itemStack = item.get().toQTItemStack();
            itemStack.hurt(1, mcTownState.level.getRandom(), null);
            if (itemStack.getDamageValue() >= itemStack.getMaxDamage()) {
                itemStack = ItemStack.EMPTY;
            }
            return setHeldItem(mcTownState, tuwn, villagerIndex, i, MCHeldItem.fromMCItemStack(itemStack));
        }
        return tuwn;
    }

    @Override
    protected boolean canInsertItem(
            Inputs mcTownState,
            MCHeldItem item,
            BlockPos bp
    ) {
        return true;
    }

    @Override
    protected ImmutableWorkStateContainer<BlockPos, MCTownState> getWorkStatuses(
            Inputs mcTownState
    ) {
        return mcTownState.town();
    }

    @Override
    protected WorkOutput<MCTownState, WorkPosition<BlockPos>> getWithSurfaceInteractionPos(
            Inputs inputs,
            WorkOutput<MCTownState, WorkPosition<BlockPos>> v
    ) {
        return Util.workWithSurfaceInteractionPos(inputs.level(), v);
    }

    @Override
    protected ArrayList<WorkPosition<BlockPos>> shuffle(
            Inputs inputs,
            Collection<WorkPosition<BlockPos>> workSpots
    ) {
        return new ArrayList<>(Compat.shuffle(ImmutableList.copyOf(workSpots), inputs.level));
    }

    @Override
    protected WorkedSpot<BlockPos> getCurWorkedSpot(
            Inputs inputs,
            MCTownState stateSource,
            BlockPos workSpot
    ) {
        int stateAfterWork = stateSource.getJobBlockState(workSpot).processingState();
        return new WorkedSpot<>(workSpot, stateAfterWork);
    }

    @Override
    protected Collection<MCHeldItem> getHeldItems(
            Inputs mcTownState,
            int villagerIndex
    ) {
        return ProductionTimeWarper.getHeldItems(mcTownState.town(), villagerIndex);
    }

    @Override
    protected void triggerCompletionAdvancement(
            Inputs inputs,
            BlockPos position
    ) {
        // Only trigger for realtime
    }

    @Override
    protected void preStateChangeHooks(
            @NotNull MCTownState ctx,
            Collection<String> rules,
            Inputs inputs,
            WorkSpot<Integer, BlockPos> position
    ) {
        PreStateChangeHook.run(
                rules, (pose) -> {
                }, (job) -> {
                    // TODO[Warp]: Set Job
                }
        );
    }

    @Override
    protected @NotNull MCTownState postInsertHook(
            @NotNull MCTownState mcTownState,
            Collection<String> rules,
            Inputs inputs,
            WorkedSpot<BlockPos> position,
            MCHeldItem item
    ) {
        return PostInsertHook.run(
                mcTownState,
                rules,
                inputs.level(),
                position,
                item.get().toMCItemStack(),
                ts -> ts.withBOPCleared(inputs.vUUID),
                inputs.vUUID
        );
    }

    @Override
    protected BlockPos getTownPos(Inputs inputs) {
        return townPos;
    }

    @Override
    protected @Nullable MCTownState preExtractHook(
            MCTownState town,
            Collection<String> rules,
            Inputs inputs,
            BlockPos position
    ) {
        Item insertedItem = null; // TODO: Support inserted item history?
        return PreExtractHook.run(
                town, rules, inputs.level(), (ctx, i, s) -> {
                    Inputs in = new Inputs(ctx, inputs.level(), inputs.vUUID());
                    return tryGiveItems(in, ImmutableList.of(i), position);
                }, (ctx, up) -> ctx.withHungerFilledBy(inputs.vUUID, up), position, insertedItem, () -> {
                }
        );
    }

    @Override
    protected MCTownState postExtractHook(
            MCTownState mcTownState,
            Collection<String> rules,
            Inputs inputs,
            BlockPos position,
            MCHeldItem extractedItem
    ) {
        return PostExtractHook.run(
                mcTownState,
                townPos,
                rules,
                inputs.level(),
                position,
                (ctx, itemData) -> {
                    CompoundTag t = extractedItem.get().toMCItemStack().getOrCreateTag();
                    itemData.forEach(t::putInt);
                    return ctx;
                }
        );
    }

    @Override
    protected MCTownState setJobBlockState(
            @NotNull Inputs inputs,
            MCTownState ts,
            BlockPos position,
            State fresh
    ) {
        return ts.setJobBlockState(position, fresh);
    }

    @Override
    protected MCTownState withEffectApplied(
            @NotNull Inputs inputs,
            MCTownState ts,
            MCHeldItem newItem
    ) {
        ItemStack s = newItem.get().toQTItemStack();
        ResourceLocation effect = EffectMetaItem.getEffect(s);
        return ts.withVillagerData(
                villagerIndex,
                ts.getVillager(villagerIndex)
                  .withEffect(new Effect(effect, EffectMetaItem.getEffectExpiry(s, Util.getTick(inputs.level))))
        );
    }

    @Override
    protected MCTownState withKnowledge(
            @NotNull Inputs inputs,
            MCTownState ts,
            MCHeldItem newItem
    ) {
        return ts.withKnowledge(newItem);
    }

    @Override
    protected boolean isInstanze(
            MCTownItem mcTownItem,
            Class<?> clazz
    ) {
        return clazz.isInstance(mcTownItem.get().asItem());
    }

    @Override
    protected boolean isMulti(MCTownItem mcTownItem) {
        return mcTownItem.toQTItemStack().getCount() > 1;
    }

    @Override
    protected MCTownState getTown(Inputs inputs) {
        return inputs.town();
    }

    @Override
    protected Iterable<MCHeldItem> getResults(
            Inputs inputs,
            Collection<MCHeldItem> mcHeldItems
    ) {
        return resultGenerator.apply(inputs.level, mcHeldItems);
    }

    @Override
    protected boolean isEntityClose(
            Inputs mcTownState,
            BlockPos position
    ) {
        return true;
    }

    @Override
    protected boolean isReady(Inputs mcTownState) {
        return true;
    }

    @Override
    public boolean tryGrabbingInsertedSupplies(Inputs mcExtra) {
        // TODO[Warp]: Implement
        return true;
    }

    @Override
    public int timesInserted(Inputs inputs) {
        // TODO[Warp]: Implement
        return 0;
    }

    @Override
    protected void registerUnmetNeed(
            Inputs inputs,
            NeedsRegistrations.Need ingredientIndex
    ) {
        // TODO[WARP]: Implement tracking of needs
    }

    @Override
    protected void registerUnmetRoom(Inputs inputs) {
        // TODO[WARP]: Implement tracking of needs
    }

    public void injectTicks(int interval) {
        ticksSinceLastAction += interval;
    }

    public JobTownProvider<MCRoom> asTownJobs(
            @NotNull State workStates,
            RoomRecipeMatch<MCRoom> mcRoom,
            BlockPos roomBlock,
            @NotNull ImmutableList<ContainerTarget<MCContainer, MCTownItem>> containers
    ) {
        return new JobTownProvider<MCRoom>() {
            @Override
            public Collection<MCRoom> roomsWithCompletedProduct() {
                if (workStates.processingState() == maxState) {
                    return ImmutableList.of(mcRoom.room);
                }
                return ImmutableList.of();
            }

            @Override
            public Collection<MCRoom> roomsAtState(Integer state) {
                if (workStates.processingState() == state) {
                    return ImmutableList.of(mcRoom.room);
                }
                return ImmutableList.of();
            }

            @Override
            public RoomsNeedingVillagerInput<MCRoom, ResourceLocation, BlockPos> roomsNeedingIngredientsByState() {
                int curState = workStates.processingState();
                PredicateCollection<MCHeldItem, ?> ings = checks.getIngredientsForStep(curState);
                if (ings != null) {
                    return new RoomsNeedingVillagerInput<>(ImmutableMap.of(
                            curState,
                            ImmutableList.of(new NVIRoom<>(mcRoom, false))
                    ));
                }

                PredicateCollection<MCTownItem, ?> toolChk = checks.getToolsForStep(curState);
                if (toolChk != null) {
                    return new RoomsNeedingVillagerInput<>(ImmutableMap.of(
                            curState,
                            ImmutableList.of(new NVIRoom<>(mcRoom, false))
                    ));
                }

                if (workStates.workLeft() > 0) {
                    return new RoomsNeedingVillagerInput<>(ImmutableMap.of(
                            curState,
                            ImmutableList.of(new NVIRoom<>(mcRoom, false))
                    ));
                }

                return new RoomsNeedingVillagerInput<>(ImmutableMap.of());
            }

            @Override
            public Map<Integer, LZCD.Dependency<Void>> roomsWithWorkableStatefulBlocks() {
                return Map.of(); // TODO[Warp]: Implement
            }

            @Override
            public LZCD.Dependency<Void> hasSuppliesV2() {
                return null; // TODO[Warp]: Implement
            }

            @Override
            public boolean isUnfinishedTimeWorkPresent() {
                return false;
            }

            @Override
            public Collection<Integer> getStatesWithUnfinishedItemlessWork() {
                Collection<Integer> statesWithUnfinishedWork = Jobs.getStatesWithUnfinishedWork(
                        () -> ImmutableList.of(() -> ImmutableList.of(
                                roomBlock)), bp -> workStates, (bp) -> true
                );
                ImmutableList.Builder<Integer> b = ImmutableList.builder();
                statesWithUnfinishedWork.forEach(state -> {
                    if (checks.getToolsForStep(state) == null) {
                        b.add(state);
                    }
                });
                return b.build();
            }

            @Override
            public boolean hasSupplies() {
                // TODO: Reduce deuplication with DeclarativeJob.roomsNeedingIngredientsOrTools
                int curState = workStates.processingState();
                PredicateCollection<MCHeldItem, ?> ings = checks.getIngredientsForStep(curState);
                if (ings != null) {
                    for (ContainerTarget<MCContainer, MCTownItem> container : containers) {
                        if (container.hasItem(i -> ings.test(MCHeldItem.fromTown(i)))) {
                            return true;
                        }
                    }
                }
                PredicateCollection<MCTownItem, ?> toolChk = checks.getToolsForStep(curState);
                if (toolChk != null) {
                    for (ContainerTarget<MCContainer, MCTownItem> container : containers) {
                        if (container.hasItem(toolChk::test)) {
                            return true;
                        }
                    }
                }
                return false;
            }

            @Override
            public boolean hasSpace() {
                return containers.stream().anyMatch(v -> !v.isFull());
            }
        }

                ;
    }

    public EntityInvStateProvider<Integer> asInventory(
            Supplier<Collection<MCHeldItem>> heldItems,
            Supplier<Integer> state
    ) {
        return new EntityInvStateProvider<>() {
            @Override
            public boolean inventoryFull() {
                Collection<MCHeldItem> items = heldItems.get();
                return items.stream().noneMatch(MCHeldItem::isEmpty);
            }

            @Override
            public boolean hasNonSupplyItems() {
                return JobsClean.hasNonSupplyItems(
                        heldItems.get(),
                        state.get(),
                        checks.getAllRequiredIngredients(),
                        Jobs.unTown(checks.getAllRequiredTools())
                );
            }

            @Override
            public Map<Integer, SupplyItemStatus> getSupplyItemStatus() {
                return JobsClean.getSupplyItemStatuses(
                        heldItems, checks.getAllRequiredIngredients(), (s) -> true, // TODO[WARP]: Implement this?
                        Jobs.unTown(checks.getAllRequiredTools()), (s) -> true, checks.getAllRequiredWork(), maxState
                );
            }
        };
    }

    public MCTownState simulateDropLoot(
            MCTownState inState,
            ProductionStatus status
    ) {
        return ProductionTimeWarper.simulateDropLoot(inState, status, villagerIndex, MCHeldItem::Air);
    }

    public @Nullable MCTownState simulateCollectSupplies(
            MCTownState inState,
            int processingState
    ) {
        Map<Integer, ? extends Predicate<MCHeldItem>> ingr = checks.getAllRequiredIngredients();
        Map<Integer, ? extends Predicate<MCHeldItem>> tool = Jobs.unTown(checks.getAllRequiredTools());
        return ProductionTimeWarper.simulateCollectSupplies(
                inState,
                processingState,
                villagerIndex,
                ingr,
                tool,
                MCHeldItem::fromTown
        );
    }

    @Override
    protected void iterate(
            Iterable<MCHeldItem> newItemsSource,
            Function<MCHeldItem, MCHeldItem> push
    ) {
        Util.iterate(newItemsSource, push::apply);
    }
}

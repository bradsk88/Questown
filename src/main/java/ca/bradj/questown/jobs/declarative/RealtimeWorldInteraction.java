package ca.bradj.questown.jobs.declarative;
import ca.bradj.questown.loader.Loader;
import ca.bradj.questown.QT;

import ca.bradj.questown.blocks.ExtractedItemAware;
import ca.bradj.questown.blocks.InsertedItemAware;
import ca.bradj.questown.core.advancements.VisitorTrigger;
import ca.bradj.questown.core.init.AdvancementsInit;
import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.integration.minecraft.MCTownItem;
import ca.bradj.questown.jobs.*;
import ca.bradj.questown.jobs.production.ProductionStatus;
import ca.bradj.questown.logic.PredicateCollection;
import ca.bradj.questown.mc.Compat;
import ca.bradj.questown.mc.Util;
import ca.bradj.questown.mobs.visitor.VisitorMobEntity;
import ca.bradj.questown.town.Claim;
import ca.bradj.questown.town.PoseInPlace;
import ca.bradj.questown.town.interfaces.ImmutableWorkStateContainer;
import ca.bradj.questown.town.workstatus.State;
import ca.bradj.questown.world.MinecraftWorldAccess;
import ca.bradj.roomrecipes.adapter.RoomRecipeMatch;
import ca.bradj.roomrecipes.serialization.MCRoom;
import com.google.common.collect.ImmutableList;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

public class RealtimeWorldInteraction extends
        AbstractWorldInteraction<MCExtra, BlockPos, MCTownItem, MCHeldItem, Boolean> {

    private final BiFunction<MCExtra, NeedsRegistrations.Need, String> getUnmetNeed;
    private final Supplier<String> jobRoom;
    private final Function<MCExtra, BlockPos> townFlagPos;
    private int soundTicksLeft;

    private final ProductionJournal<MCTownItem, MCHeldItem> journal;
    private final BiFunction<ServerLevel, Collection<MCHeldItem>, Iterable<MCHeldItem>> resultGenerator;
    private final @Nullable SoundInfo sound;

    public RealtimeWorldInteraction(
            Function<MCExtra, BlockPos> townFlagPos,
            ProductionJournal<MCTownItem, MCHeldItem> journal,
            int maxState,
            DeclarativeJobChecks<MCExtra, MCHeldItem, MCTownItem, RoomRecipeMatch<MCRoom>, BlockPos> checks,
            Map<ProductionStatus, Collection<String>> specialRules,
            BiFunction<ServerLevel, Collection<MCHeldItem>, Iterable<MCHeldItem>> resultGenerator,
            Function<MCExtra, Claim> claimSpots,
            BiFunction<MCExtra, NeedsRegistrations.Need, String> getUnmetNeed,
            Supplier<String> jobRoom,
            int interval,
            @Nullable SoundInfo sound
    ) {
        super(
                journal.getJobId(), -1,
                // Not used by this implementation
                interval, maxState, checks, claimSpots, specialRules
        );
        this.getUnmetNeed = getUnmetNeed;
        this.jobRoom = jobRoom;
        this.journal = journal;
        this.resultGenerator = resultGenerator;
        this.sound = sound;
        super.addItemInsertionListener((extra, bp, item) -> {
            Block block = extra.town().getServerLevel().getBlockState(bp).getBlock();
            if (block instanceof InsertedItemAware iia) {
                iia.handleInsertedItem(extra, bp, item);
            }
        });
        super.addItemExtractionListener((extra, bp) -> {
            Block block = extra.town().getServerLevel().getBlockState(bp).getBlock();
            if (block instanceof ExtractedItemAware iia) {
                iia.handleExtractedItem(extra, bp);
            }
        });
        this.townFlagPos = townFlagPos;
    }

    @Override
    protected int getWorkSpeedOf10(MCExtra mcExtra) {
        java.util.UUID uuid = mcExtra.entity().getUUID();
        int base = mcExtra.town().getVillagerHandle().getWorkSpeed(uuid);
        float mult = proficiencyMultiplier(
                getProficiencyId(),
                id -> mcExtra.town().getVillagerHandle().getProficiency(uuid, id)
        );
        return applyProficiencyToWorkSpeed(base, mult);
    }

    @Override
    protected @Nullable String getProficiencyId() {
        return ca.bradj.questown.jobs.ServerJobsRegistry.getProficiencyId(getJobId());
    }

    @Override
    protected int getAffectedTime(
            MCExtra mcExtra,
            Integer timeToAugment
    ) {
        return mcExtra.town().getVillagerHandle().getAffectedTime(mcExtra.entity().getUUID(), timeToAugment);
    }

    @Override
    protected Boolean onWorkActionCompleted(
            MCExtra mcExtra,
            Boolean town
    ) {
        String profId = getProficiencyId();
        if (profId == null) {
            return town;
        }
        java.util.UUID uuid = mcExtra.entity().getUUID();
        var handle = mcExtra.town().getVillagerHandle();
        handle.setProficiencies(uuid, levelProficiencies(handle.getProficiencies(uuid), profId));
        return town;
    }

    @Override
    protected Boolean setHeldItem(
            MCExtra uxtra,
            Boolean tuwn,
            int villagerIndex,
            int itemIndex,
            MCHeldItem item
    ) {
        journal.setItem(itemIndex, item);
        return true;
    }

    @Override
    protected Boolean degradeTool(
            MCExtra mcExtra,
            Boolean tuwn,
            PredicateCollection<MCTownItem, ?> toolCheck
    ) {
        Optional<MCHeldItem> foundTool = journal.getItems().stream().filter(v -> toolCheck.test(v.get())).findFirst();
        if (foundTool.isPresent()) {
            int idx = journal.getItems().indexOf(foundTool.get());
            ItemStack is = foundTool.get().get().toQTItemStack();
            is.hurtAndBreak(
                    1,
                    mcExtra.entity().level().getRandom(),
                    mcExtra.entity(),
                    () -> {}
            );
            journal.setItem(idx, MCHeldItem.fromMCItemStack(is));
            return true;
        }
        return null;
    }

    @Override
    protected ImmutableWorkStateContainer<BlockPos, Boolean> getWorkStatuses(
            MCExtra extra
    ) {
        return extra.work();
    }

    @Override
    protected @Nullable ServerLevel getServerLevel(MCExtra extra) {
        return extra.town().getServerLevel();
    }

    @Override
    protected WorkOutput<Boolean, WorkPosition<BlockPos>> getWithSurfaceInteractionPos(
            MCExtra mcExtra,
            WorkOutput<Boolean, WorkPosition<BlockPos>> v
    ) {
        return Util.workWithSurfaceInteractionPos(getServerLevel(mcExtra), v);
    }

    @Override
    protected Collection<MCHeldItem> getHeldItems(
            MCExtra mcExtra,
            int villagerIndex
    ) {
        return journal.getItems();
    }

    @Override
    protected Boolean setJobBlockState(
            @NotNull MCExtra inputs,
            Boolean ts,
            BlockPos position,
            State state
    ) {
        inputs.work().setJobBlockState(position, state);
        return true;
    }

    @Override
    protected boolean isMulti(MCTownItem mcTownItem) {
        return mcTownItem.toQTItemStack().getCount() > 1;
    }

    @Override
    protected Boolean getTown(MCExtra inputs) {
        return true; // TODO: Is this right?
    }

    @Override
    protected Iterable<MCHeldItem> getResults(
            MCExtra inputs,
            Collection<MCHeldItem> mcHeldItems
    ) {
        return withLootGuarantees(
                resolveFlagFromTown(inputs),
                resultGenerator.apply(inputs.town().getServerLevel(), mcHeldItems)
        );
    }

    private static ca.bradj.questown.town.entity.TownFlagBlockEntity resolveFlagFromTown(MCExtra inputs) {
        if (inputs.town() instanceof ca.bradj.questown.town.entity.TownFlagBlockEntity flag) {
            return flag;
        }
        return flagBlockEntityAt(
                inputs.town().getServerLevel(),
                inputs.town().getTownFlagBasePos()
        );
    }

    @Override
    protected boolean canInsertItem(
            MCExtra mcExtra,
            MCHeldItem item,
            BlockPos bp
    ) {
        return mcExtra.work().canInsertItem(item, bp);
    }

    @Override
    public @Nullable WorkOutput<Boolean, WorkPosition<BlockPos>> tryWorking(
            MCExtra mcExtra,
            WorkPosition<BlockPos> workSpot
    ) {
        @Nullable WorkOutput<@Nullable Boolean, WorkPosition<BlockPos>> o = super.tryWorking(mcExtra, workSpot);
        if (o != null && o.town() != null && o.town()) {
            playSound(mcExtra, o.spot().jobBlock());
            mcExtra.entity().swing(InteractionHand.MAIN_HAND);
        }
        return o;
    }

    @Override
    protected WorkedSpot<BlockPos> getCurWorkedSpot(
            MCExtra mcExtra,
            Boolean stateSource,
            BlockPos workSpot
    ) {
        State jobBlockState = getJobBlockState(mcExtra, workSpot);
        return new WorkedSpot<>(workSpot, Util.withFallbackForNullInput(jobBlockState, State::processingState, 0));
    }

    private void playSound(
            MCExtra mcExtra,
            BlockPos pos
    ) {
        if (sound == null) {
            return;
        }
        @Nullable SoundEvent s = Loader.soundFromId(sound.sound());
        int dieRoll = mcExtra.town().getServerLevel().getRandom().nextInt(100);
        int chance = sound.chance() == null ? 10 : sound.chance();
        if (dieRoll < chance) {
            this.soundTicksLeft = (sound.duration() == null ? 5 : sound.duration());
        }
        if (Math.max(this.soundTicksLeft--, 0) > 0) {
            Compat.playNeutralSound(mcExtra.town().getServerLevel(), pos, s);
        }
    }

    @Override
    protected boolean isEntityClose(
            MCExtra extra,
            BlockPos position
    ) {
        return Jobs.isCloseTo(extra.entity().blockPosition(), position);
    }

    @Override
    protected boolean isReady(MCExtra extra) {
        return extra.town() != null && extra.town().getServerLevel() != null;
    }

    @Override
    public boolean tryGrabbingInsertedSupplies(
            MCExtra mcExtra
    ) {
        VisitorMobEntity.WorkToUndo wtu = mcExtra.entity().getWorkToUndo();
        boolean res;
        if (wtu == null) {
            res = true;
        } else {
            res = tryGiveItems(mcExtra, wtu.itemsInserted(), wtu.pos());
        }
        // TEMP DEBUG (2026-10-06) - throttled: the give-back latch is the suspected
        // permanent stall (job=COLLECTING_SUPPLIES, no extraction ever).
        return res;
    }


    @Override
    public int timesInserted(MCExtra mcExtra) {
        VisitorMobEntity.WorkToUndo workToUndo = mcExtra.entity().getWorkToUndo();
        return workToUndo == null ? 0 : workToUndo.itemsInserted().size();
    }

    @Override
    protected void registerUnmetNeed(
            MCExtra mcExtra,
            NeedsRegistrations.Need need
    ) {
        ServerLevel serverLevel = mcExtra.town().getServerLevel();
        if (serverLevel == null) {
            throw new UnsupportedOperationException("Cannot run without server level");
        }
        String apply = getUnmetNeed.apply(mcExtra, need);
        if (apply == null) {
            return;
        }
        mcExtra.town().getEconomicsHandle()
               .registerUnmetNeed(Util.getTick(serverLevel), mcExtra.entity().getUUID(), apply);
    }

    @Override
    protected void registerUnmetRoom(
            MCExtra mcExtra
    ) {
        ServerLevel serverLevel = mcExtra.town().getServerLevel();
        if (serverLevel == null) {
            throw new UnsupportedOperationException("Cannot run without server level");
        }
        mcExtra.town().getEconomicsHandle()
               .registerUnmetRoom(Util.getTick(serverLevel), mcExtra.entity().getUUID(), jobRoom.get());

    }

    public void clearInsertedSupplies(MCExtra extra) {
        extra.entity().clearWorkToUndo();
    }

    @Override
    protected @Nullable Boolean preExtractHook(
            Boolean didAnything,
            Collection<String> rules,
            MCExtra inputs,
            BlockPos position
    ) {
        VisitorMobEntity.WorkToUndo workToUndo = inputs.entity().getWorkToUndo();
        return PreExtractHook.run(
                didAnything,
                rules,
                new MinecraftWorldAccess(inputs.town().getServerLevel()),
                (in, i, s) -> {
                    inputs.entity().tryGiveItem(i, s);
                    // A rule-taken product (cook/extract's take_from_slot_2) is an extraction too:
                    // it gets the LOOT-quest check like a result item (warp routes it through
                    // tryGiveItems -> postExtractHook already). 2026-10-19: the cook's meals never
                    // counted toward the "eight pieces of food" quest.
                    onLootExtracted(inputs, i);
                    return in;
                },
                position,
                last(workToUndo),
                () -> inputs.town().getVillagerHandle().clearPoseRequests(inputs.entity().getUUID()),
                () -> ImmutableList.of(position)
        );
    }

    private Item last(VisitorMobEntity.WorkToUndo workToUndo) {
        if (workToUndo == null) {
            return null;
        }
        ImmutableList<MCHeldItem> ii = workToUndo.itemsInserted();
        if (ii.isEmpty()) {
            return null;
        }
        return ii.get(ii.size() -1).get().get();
    }

    @Override
    protected Boolean postExtractHook(
            Boolean aBoolean,
            Collection<String> rules,
            MCExtra inputs,
            BlockPos position,
            @Nullable MCHeldItem extractedItem
    ) {
        Boolean hooked = PostExtractHook.run(
                aBoolean,
                inputs.town().getTownFlagBasePos(),
                rules,
                new MinecraftWorldAccess(inputs.town().getServerLevel()),
                position,
                (town, itemData) -> {
                    if (extractedItem == null || extractedItem.isEmpty()) {
                        return town;
                    }
                    CompoundTag t = Compat.itemDataTag(extractedItem.get().toMCItemStack());
                    itemData.forEach(t::putInt);
                    return town;
                },
                (in, up) -> {
                    inputs.town().getVillagerHandle().eat(inputs.entity().getUUID(), up);
                    return in;
                },
                (in, effect, durationTicks) -> {
                    long expiry = Util.getTick(inputs.town().getServerLevel()) + durationTicks;
                    inputs.town().getVillagerHandle().applyEffect(effect, expiry, inputs.entity().getUUID());
                    return in;
                },
                extractedItem,
                (in, foundLoot) -> {
                    inputs.town().getKnowledgeHandle().registerFoundLoots(ImmutableList.of(foundLoot));
                    return in;
                }
        );
        // The LOOT-quest close runs in onLootExtracted (the abstract 3-arg bridge's
        // unconditional extract path), so a job that declares no EXTRACTING_PRODUCT
        // special rule (e.g. the sword hunter) still closes its raw-food quest.
        // The item itself is not consumed — the deposit / eat path still decides its fate.
        return hooked;
    }

    /**
     * Close matching LOOT quests on a non-empty extraction. Lives on the
     * unconditional extract path (the abstract 3-arg {@code postExtractHook}
     * bridge) rather than the rule-gated 5-arg hook, so a hunt that declares no
     * {@code EXTRACTING_PRODUCT} special rule (e.g. the sword hunter) still
     * closes its quest. See {@link LootExtractGuard}.
     */
    @Override
    protected void onLootExtracted(MCExtra inputs, @Nullable MCHeldItem item) {
        if (LootExtractGuard.questCheckFires(
                item != null && !item.isEmpty(),
                hasExtractingProductRule())) {
            ca.bradj.questown.town.entity.TownFlagBlockEntity flag = resolveFlagFromTown(inputs);
            boolean rawWasActive = FirstHunterLootGuarantee.rawFoodLootQuestActive(flag)
                    && flag != null && "hunter".equals(getJobId() != null ? getJobId().rootId() : null)
                    && !flag.isFirstHunterLootGuaranteeFired();
            inputs.town().getQuestHandle().processLootQuests(item.get());
            if (rawWasActive && FirstHunterLootGuarantee.matchesFirstHunterItem(item)) {
                FirstHunterLootGuarantee.flipOnceBit(flag);
            }
        }
    }

    @Override
    protected @NotNull Boolean postInsertHook(
            @NotNull Boolean aBoolean,
            Collection<String> rules,
            MCExtra inputs,
            WorkedSpot<BlockPos> position,
            MCHeldItem item
    ) {
        return PostInsertHook.run(
                aBoolean, rules, new MinecraftWorldAccess(inputs.town().getServerLevel()), position, item.get().toMCItemStack(), (t) -> {
                    inputs.town().getVillagerHandle().clearBlockOfProgress(inputs.entity().getUUID());
                    return true;
                }, inputs.entity().getUUID()
        );
    }

    @Override
    protected void preStateChangeHooks(
            @NotNull Boolean ctx,
            Collection<String> rules,
            MCExtra inputs,
            WorkSpot<Integer, BlockPos> position
    ) {
        PreStateChangeHook.run(
                rules,
                pose -> inputs.town().getVillagerHandle().requestPose(
                        inputs.entity().getUUID(),
                        new PoseInPlace(pose, decideSpot(rules, position))
                ),
                jobId -> inputs.town().getVillagerHandle().changeJobForVillager(inputs.entity().getUUID(), jobId, false)
        );
    }

    private static BlockPos decideSpot(
            Collection<String> rules,
            WorkSpot<Integer, BlockPos> position
    ) {
        if (rules.contains(SpecialRules.PREFER_INTERACTION_STAND_ON_TOP)) {
            return position.workPos().jobBlock().above();
        }
        return position.workPos().entityFeetPos();
    }

    @Override
    protected BlockPos getTownPos(MCExtra inputs) {
        return townFlagPos.apply(inputs);
    }

    @Override
    protected void triggerCompletionAdvancement(MCExtra inputs,
                                                BlockPos position
    ) {
        AdvancementsInit.VISITOR_TRIGGER.triggerForNearestPlayer(
                inputs.town().getServerLevel(),
                VisitorTrigger.Triggers.FirstJobDone,
                position
        );
    }
}

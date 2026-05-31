package ca.bradj.questown.jobs.declarative;

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
import com.google.common.collect.ImmutableMap;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
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

    private static ImmutableMap<Integer, Function<MCTownItem, Boolean>> stripMC2(
            ImmutableMap<Integer, Ingredient> toolsRequiredAtStates
    ) {
        ImmutableMap.Builder<Integer, Function<MCTownItem, Boolean>> b = ImmutableMap.builder();
        toolsRequiredAtStates.forEach((k, v) -> b.put(k, z -> v.test(z.toQTItemStack())));
        return b.build();
    }

    private static ImmutableMap<Integer, Function<MCHeldItem, Boolean>> stripMC(ImmutableMap<Integer, Ingredient> ingredientsRequiredAtStates) {
        ImmutableMap.Builder<Integer, Function<MCHeldItem, Boolean>> b = ImmutableMap.builder();
        ingredientsRequiredAtStates.forEach((k, v) -> b.put(k, z -> v.test(z.get().toQTItemStack())));
        return b.build();
    }

    @Override
    protected int getWorkSpeedOf10(MCExtra mcExtra) {
        return Math.max(mcExtra.town().getVillagerHandle().getWorkSpeed(mcExtra.entity().getUUID()), 1);
    }

    @Override
    protected int getAffectedTime(
            MCExtra mcExtra,
            Integer timeToAugment
    ) {
        return mcExtra.town().getVillagerHandle().getAffectedTime(mcExtra.entity().getUUID(), timeToAugment);
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
                    1, mcExtra.entity(), (x) -> {
                    }
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
    protected ArrayList<WorkPosition<BlockPos>> shuffle(
            MCExtra mcExtra,
            Collection<WorkPosition<BlockPos>> workSpots
    ) {
        return new ArrayList<>(Compat.shuffle(ImmutableList.copyOf(workSpots), mcExtra.town().getServerLevel()));
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
        Iterable<MCHeldItem> base = resultGenerator.apply(inputs.town().getServerLevel(), mcHeldItems);
        return ca.bradj.questown.mobs.helperchicken.ChickenArcLootGuarantee.maybePrepend(
                resolveFlagFromTown(inputs), base
        );
    }

    private static ca.bradj.questown.town.entity.TownFlagBlockEntity resolveFlagFromTown(MCExtra inputs) {
        if (inputs.town() instanceof ca.bradj.questown.town.entity.TownFlagBlockEntity flag) {
            return flag;
        }
        net.minecraft.server.level.ServerLevel level = inputs.town().getServerLevel();
        if (level == null) {
            return null;
        }
        net.minecraft.core.BlockPos flagPos = inputs.town().getTownFlagBasePos();
        if (level.getBlockEntity(flagPos) instanceof ca.bradj.questown.town.entity.TownFlagBlockEntity flag) {
            return flag;
        }
        return null;
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
        @Nullable SoundEvent s = ForgeRegistries.SOUND_EVENTS.getValue(sound.sound());
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
        if (wtu == null) {
            return true;
        }
        return tryGiveItems(mcExtra, wtu.itemsInserted(), wtu.pos());
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
        return PostExtractHook.run(
                aBoolean,
                inputs.town().getTownFlagBasePos(),
                rules,
                new MinecraftWorldAccess(inputs.town().getServerLevel()),
                position,
                (town, itemData) -> {
                    if (extractedItem == null || extractedItem.isEmpty()) {
                        return town;
                    }
                    CompoundTag t = extractedItem.get().toMCItemStack().getOrCreateTag();
                    itemData.forEach(t::putInt);
                    return town;
                },
                (in, up) -> {
                    inputs.town().getVillagerHandle().fillHunger(inputs.entity().getUUID(), up);
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
    protected WorkOutput<Boolean, WorkPosition<BlockPos>> getWithSurfaceInteractionPos(
            MCExtra mcExtra,
            WorkOutput<Boolean, WorkPosition<BlockPos>> v
    ) {
        return Util.workWithSurfaceInteractionPos(mcExtra.town().getServerLevel(), v);
    }

    @Override
    protected void iterate(
            Iterable<MCHeldItem> newItemsSource,
            Function<MCHeldItem, MCHeldItem> push
    ) {
        Util.iterate(newItemsSource, push::apply);
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

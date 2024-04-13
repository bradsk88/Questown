package ca.bradj.questown.jobs.declarative;

import ca.bradj.questown.blocks.InsertedItemAware;
import ca.bradj.questown.integration.minecraft.MCContainer;
import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.integration.minecraft.MCTownItem;
import ca.bradj.questown.items.EffectMetaItem;
import ca.bradj.questown.jobs.*;
import ca.bradj.questown.jobs.leaver.ContainerTarget;
import ca.bradj.questown.mc.Compat;
import ca.bradj.questown.mc.Util;
import ca.bradj.questown.mobs.visitor.VisitorMobEntity;
import ca.bradj.questown.town.AbstractWorkStatusStore;
import ca.bradj.questown.town.Claim;
import ca.bradj.questown.town.TownContainers;
import ca.bradj.questown.town.interfaces.ImmutableWorkStateContainer;
import ca.bradj.questown.town.interfaces.TownInterface;
import ca.bradj.questown.town.interfaces.WorkStatusHandle;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;

public class RealtimeWorldInteraction
        extends AbstractWorldInteraction<MCExtra, BlockPos, MCTownItem, MCHeldItem, Boolean> {

    private int soundTicksLeft;

    public WorkOutput<Boolean, WorkSpot<Integer, BlockPos>> tryWorking(
            TownInterface town,
            WorkStatusHandle<BlockPos, MCHeldItem> work,
            VisitorMobEntity entity,
            Collection<WorkSpot<Integer, BlockPos>> workSpots
    ) {
        ArrayList<WorkSpot<Integer, BlockPos>> shuffled = new ArrayList<>(workSpots);
        Compat.shuffle(shuffled, town.getServerLevel());
        for (WorkSpot<Integer, BlockPos> workSpot : shuffled) {
            WorkOutput<Boolean, WorkSpot<Integer, BlockPos>> v = tryWorking(new MCExtra(town, work, entity), workSpot);
            if (v != null) {
                return v;
            }
        }
        return new WorkOutput<>(
                null, ImmutableList.copyOf(shuffled)
                                   .get(0));
    }

    private final ItemsHolder<MCHeldItem> journal;
    private final ImmutableMap<Integer, Integer> ingredientQtyRequiredAtStates;
    private final BiFunction<ServerLevel, Collection<MCHeldItem>, Iterable<MCHeldItem>> resultGenerator;
    private final @Nullable ResourceLocation sound;

    public RealtimeWorldInteraction(
            JobID jobId,
            ItemsHolder<MCHeldItem> journal,
            int maxState,
            ImmutableMap<Integer, Ingredient> ingredientsRequiredAtStates,
            ImmutableMap<Integer, Integer> ingredientQtyRequiredAtStates,
            ImmutableMap<Integer, Integer> workRequiredAtStates,
            ImmutableMap<Integer, Integer> timeRequiredAtStates,
            ImmutableMap<Integer, Ingredient> toolsRequiredAtStates,
            Function<Integer, Collection<String>> specialRules,
            BiFunction<ServerLevel, Collection<MCHeldItem>, Iterable<MCHeldItem>> resultGenerator,
            Function<MCExtra, Claim> claimSpots,
            int interval,
            @Nullable ResourceLocation sound
    ) {
        super(
                jobId,
                -1, // Not used by this implementation
                interval,
                maxState,
                stripMC2(toolsRequiredAtStates),
                workRequiredAtStates,
                stripMC(ingredientsRequiredAtStates),
                ingredientQtyRequiredAtStates,
                timeRequiredAtStates,
                claimSpots,
                specialRules
        );
        this.journal = journal;
        this.ingredientQtyRequiredAtStates = ingredientQtyRequiredAtStates;
        this.resultGenerator = resultGenerator;
        this.sound = sound;
        super.addItemInsertionListener((extra, bp, item) -> {
            Block block = extra.town()
                               .getServerLevel()
                               .getBlockState(bp)
                               .getBlock();
            if (block instanceof InsertedItemAware iia) {
                iia.handleInsertedItem(extra, bp, item);
            }
        });
    }

    private static ImmutableMap<Integer, Function<MCTownItem, Boolean>> stripMC2(
            ImmutableMap<Integer, Ingredient> toolsRequiredAtStates
    ) {
        ImmutableMap.Builder<Integer, Function<MCTownItem, Boolean>> b = ImmutableMap.builder();
        toolsRequiredAtStates.forEach((k, v) -> b.put(k, z -> v.test(z.toItemStack())));
        return b.build();
    }

    private static ImmutableMap<Integer, Function<MCHeldItem, Boolean>> stripMC(ImmutableMap<Integer, Ingredient> ingredientsRequiredAtStates) {
        ImmutableMap.Builder<Integer, Function<MCHeldItem, Boolean>> b = ImmutableMap.builder();
        ingredientsRequiredAtStates.forEach((k, v) -> b.put(k, z -> v.test(z.get()
                                                                            .toItemStack())));
        return b.build();
    }

    @Override
    protected boolean isWorkResult(
            MCExtra mcExtra,
            MCHeldItem item
    ) {
        return Works.isWorkResult(mcExtra.town().getTownData(), item.toItem());
    }

    @Override
    protected int getWorkSpeedOf10(MCExtra mcExtra) {
        return Math.max(
                mcExtra.town()
                       .getVillagerHandle()
                       .getWorkSpeed(mcExtra.entity()
                                            .getUUID()),
                1
        );
    }

    @Override
    protected int getAffectedTime(
            MCExtra mcExtra,
            Integer timeToAugment
    ) {
        return mcExtra.town()
                      .getVillagerHandle()
                      .getAffectedTime(mcExtra.entity()
                                              .getUUID(), timeToAugment);
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
            Function<MCTownItem, Boolean> toolCheck
    ) {
        Optional<MCHeldItem> foundTool = journal.getItems()
                                                .stream()
                                                .filter(v -> toolCheck.apply(v.get()))
                                                .findFirst();
        if (foundTool.isPresent()) {
            int idx = journal.getItems()
                             .indexOf(foundTool.get());
            ItemStack is = foundTool.get()
                                    .get()
                                    .toItemStack();
            is.hurtAndBreak(1, mcExtra.entity(), (x) -> {
            });
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
    protected Collection<MCHeldItem> getHeldItems(
            MCExtra mcExtra,
            int villagerIndex
    ) {
        return journal.getItems();
    }

    @Override
    protected Boolean addToNearbyChest(
            @NotNull MCExtra inputs,
            Boolean updatedTown,
            BlockPos chestPosition,
            MCTownItem stackWithQuantity
    ) {
        if (!Jobs.isCloseTo(inputs.entity().blockPosition(), chestPosition)) {
            return false;
        }
        BlockState bs = inputs.town().getServerLevel().getBlockState(chestPosition);
        if (!(bs.getBlock() instanceof ChestBlock cb)) {
            return false;
        }
        @NotNull ContainerTarget<MCContainer, MCTownItem> cont = TownContainers.fromChestBlock(
                chestPosition,
                cb,
                inputs.town().getServerLevel()
        );
        Map.Entry<ContainerTarget<MCContainer, MCTownItem>, MCTownItem> add = cont.withItemAdded(stackWithQuantity);
        return add != null;
    }

    @Override
    protected MCTownItem createStackWithQuantity(
            MCTownItem item,
            int qy
    ) {
        return item.withQuantity(qy);
    }

    @Override
    protected @Nullable MCTownItem getLastInsertedIngredients(
            MCExtra inputs,
            int villagerIndex
    ) {
        @Nullable VisitorMobEntity.WorkToUndo workToUndo = inputs.entity().getWorkToUndo();
        if (workToUndo == null) {
            return null;
        }
        return workToUndo.item().get();
    }

    @Override
    protected Boolean setJobBlockState(
            @NotNull MCExtra inputs,
            Boolean ts,
            BlockPos position,
            AbstractWorkStatusStore.State state
    ) {
        inputs.work()
              .setJobBlockState(position, state);
        return true;
    }

    @Override
    protected Boolean withEffectApplied(
            @NotNull MCExtra inputs,
            Boolean ts,
            MCHeldItem newItem
    ) {
        ItemStack stack = newItem.get()
                                 .toItemStack();
        ResourceLocation effect = EffectMetaItem.getEffect(stack);
        Long effectExpiry = EffectMetaItem.getEffectExpiry(
                stack, Util.getTick(inputs.town()
                                          .getServerLevel()));
        inputs.town()
              .getVillagerHandle()
              .applyEffect(
                      effect,
                      effectExpiry,
                      inputs.entity()
                            .getUUID()
              );
        return null;
    }

    @Override
    protected Boolean withKnowledge(
            @NotNull MCExtra inputs,
            Boolean ts,
            MCHeldItem newItem
    ) {
        inputs.town()
              .getKnowledgeHandle()
              .registerFoundLoots(ImmutableList.of(newItem));
        return null;
    }

    @Override
    protected boolean isInstanze(
            MCTownItem mcTownItem,
            Class<?> clazz
    ) {
        return clazz.isInstance(mcTownItem.get());
    }

    @Override
    protected boolean isMulti(MCTownItem mcTownItem) {
        return mcTownItem.toItemStack()
                         .getCount() > 1;
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
        return resultGenerator.apply(inputs.town()
                                           .getServerLevel(), mcHeldItems);
    }

    @Override
    protected boolean canInsertItem(
            MCExtra mcExtra,
            MCHeldItem item,
            BlockPos bp
    ) {
        return mcExtra.work()
                      .canInsertItem(item, bp);
    }

    @Override
    public Map<Integer, Integer> ingredientQuantityRequiredAtStates() {
        return ingredientQtyRequiredAtStates;
    }

    @Override
    public @Nullable WorkOutput<Boolean, WorkSpot<Integer, BlockPos>> tryWorking(
            MCExtra mcExtra,
            WorkSpot<Integer, BlockPos> workSpot
    ) {
        @Nullable WorkOutput<@Nullable Boolean, WorkSpot<Integer, BlockPos>> o = super.tryWorking(
                mcExtra,
                workSpot
        );
        if (o != null && o.town() != null && o.town()) {
            playSound(
                    mcExtra, o.spot()
                              .interactionSpot());
        }
        return o;
    }

    private void playSound(
            MCExtra mcExtra,
            BlockPos pos
    ) {
        @Nullable SoundEvent s = ForgeRegistries.SOUND_EVENTS.getValue(sound);
        int soundChance = 10; // TODO[ASAP]: Get from job
        if (mcExtra.town()
                   .getServerLevel()
                   .getRandom()
                   .nextInt(soundChance) == 0) {
            this.soundTicksLeft = 5;
        }
        if (Math.max(this.soundTicksLeft--, 0) > 0) {
            Compat.playNeutralSound(mcExtra.town()
                                           .getServerLevel(), pos, s);
        }
    }

    @Override
    protected boolean isEntityClose(
            MCExtra extra,
            BlockPos position
    ) {
        return Jobs.isCloseTo(extra.entity()
                                   .blockPosition(), position);
    }

    @Override
    protected boolean isReady(MCExtra extra) {
        return extra.town() != null && extra.town()
                                            .getServerLevel() != null;
    }

    public boolean tryGrabbingInsertedSupplies(
            TownInterface town,
            WorkStatusHandle<BlockPos, MCHeldItem> work,
            VisitorMobEntity vmEntity
    ) {
        MCExtra mcExtra = new MCExtra(town, work, vmEntity);
        VisitorMobEntity.WorkToUndo wtu = vmEntity.getWorkToUndo();
        if (wtu == null) {
            return true;
        }
        return tryGiveItems(mcExtra, true, ImmutableList.of(wtu.item()), wtu.pos());
    }
}

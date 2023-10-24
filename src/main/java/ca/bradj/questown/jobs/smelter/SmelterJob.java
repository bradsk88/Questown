package ca.bradj.questown.jobs.smelter;

import ca.bradj.questown.QT;
import ca.bradj.questown.Questown;
import ca.bradj.questown.blocks.SmeltingOvenBlock;
import ca.bradj.questown.core.init.TagsInit;
import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.integration.minecraft.MCTownItem;
import ca.bradj.questown.jobs.*;
import ca.bradj.questown.mobs.visitor.VisitorMobEntity;
import ca.bradj.questown.town.interfaces.TownInterface;
import ca.bradj.roomrecipes.adapter.Positions;
import ca.bradj.roomrecipes.adapter.RoomRecipeMatch;
import ca.bradj.roomrecipes.logic.InclusiveSpaces;
import ca.bradj.roomrecipes.serialization.MCRoom;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

public class SmelterJob extends ProductionJob<SmelterStatus, SimpleSnapshot<SmelterStatus, MCHeldItem>, SmelterJournal<MCTownItem, MCHeldItem>> {

    ResourceLocation WORKSITE_ID = new ResourceLocation(Questown.MODID, "smeltery");

    private static final ImmutableList<MCTownItem> allowedToPickUp = ImmutableList.of(
            MCTownItem.fromMCItemStack(Items.COAL.getDefaultInstance()),
            MCTownItem.fromMCItemStack(Items.IRON_ORE.getDefaultInstance()),
            MCTownItem.fromMCItemStack(Items.RAW_IRON.getDefaultInstance())
    );

    private static final ImmutableList<ProductionJob.TestFn<SmelterStatus, MCTownItem>> recipe = ImmutableList.of(
            new TestFn<>() {
                @Override
                public boolean test(
                        Map<SmelterStatus, Boolean> canUseIngredientsForWork,
                        ca.bradj.questown.integration.minecraft.MCTownItem item
                ) {
                    if (canUseIngredientsForWork.getOrDefault(SmelterStatus.WORK_PROCESSING_ORE, false)) {
                        return testAssumeNeeded(item);
                    }
                    return false;
                }

                @Override
                public boolean testAssumeNeeded(ca.bradj.questown.integration.minecraft.MCTownItem item) {
                    return Ingredient.of(TagsInit.Items.PICKAXES).test(item.toItemStack());
                }
            },
            new TestFn<>() {
                @Override
                public boolean test(
                        Map<SmelterStatus, Boolean> canUseIngredientsForWork,
                        ca.bradj.questown.integration.minecraft.MCTownItem item
                ) {
                    if (canUseIngredientsForWork.getOrDefault(SmelterStatus.WORK_INSERTING_ORE, false)) {
                        return testAssumeNeeded(item);
                    }
                    return false;
                }

                @Override
                public boolean testAssumeNeeded(ca.bradj.questown.integration.minecraft.MCTownItem item) {
                    return Ingredient.of(Items.IRON_ORE).test(item.toItemStack());
                }
            }

    );

    private static final Marker marker = MarkerManager.getMarker("Smelter");
    private final WorldInteraction world;
    private Signals signal;
    private WorkSpot<SmelterAction, BlockPos> workSpot;

    public SmelterJob(
            UUID ownerUUID,
            int inventoryCapacity
    ) {
        super(ownerUUID, inventoryCapacity, allowedToPickUp, recipe, marker);
        this.world = new WorldInteraction(inventory, journal);
    }

    @Override
    protected SmelterJournal<MCTownItem, MCHeldItem> getInitializedJournal(int inventoryCapacity) {
        return new SmelterJournal<>(
                () -> this.signal,
                inventoryCapacity,
                MCHeldItem::Air
        );
    }

    @Override
    protected void tick(
            TownInterface town,
            LivingEntity entity,
            Direction facingPos,
            Map<SmelterStatus, ? extends Collection<MCRoom>> roomsNeedingIngredients
    ) {
        this.signal = Signals.fromGameTime(town.getServerLevel().getDayTime());
        JobTownProvider<SmelterStatus, MCRoom> jtp = new JobTownProvider<>() {
            @Override
            public Collection<MCRoom> roomsWithCompletedProduct() {
                return Jobs.roomsWithState(town, SmeltingOvenBlock::hasOreToCollect);
            }

            @Override
            public Map<SmelterStatus, ? extends Collection<MCRoom>> roomsNeedingIngredients() {
                return roomsNeedingIngredients;
            }

            @Override
            public boolean hasSupplies() {
                Map<SmelterStatus, ? extends Collection<MCRoom>> needs = roomsNeedingIngredients();
                return Jobs.townHasSupplies(town, journal, convertToCleanFns(needs));
            }

            @Override
            public boolean hasSpace() {
                return Jobs.townHasSpace(town);
            }
        };
        BlockPos entityBlockPos = entity.blockPosition();
        RoomRecipeMatch<MCRoom> entityCurrentJobSite = Jobs.getEntityCurrentJobSite(town, WORKSITE_ID, entityBlockPos);
        EntityLocStateProvider<MCRoom> elp = new EntityLocStateProvider<>() {
            @Override
            public @Nullable RoomRecipeMatch<MCRoom> getEntityCurrentJobSite() {

                return entityCurrentJobSite;
            }
        };
        this.journal.tick(jtp, elp, super.defaultEntityInvProvider());

        if (entityCurrentJobSite != null) {
            tryWorking(town, entity, entityCurrentJobSite);
        }
        tryDropLoot(entityBlockPos);
        tryGetSupplies(jtp, entityBlockPos);
    }

    private void tryWorking(
            TownInterface town,
            LivingEntity entity,
            @NotNull RoomRecipeMatch<MCRoom> entityCurrentJobSite
    ) {
        Map<SmelterAction, WorkSpot<SmelterAction, BlockPos>> workSpots = listAllWorkspots(
                town.getServerLevel(),
                entityCurrentJobSite.room
        );

        this.workSpot = switch (getStatus()) {
            case WORK_INSERTING_ORE -> workSpots.getOrDefault(SmelterAction.INSERT_ORE, null);
            case WORK_PROCESSING_ORE -> workSpots.getOrDefault(SmelterAction.PROCESSS_ORE, null);
            case WORK_COLLECTING_RAW_PRODUCT -> workSpots.getOrDefault(SmelterAction.COLLECT_RAW_PRODUCT, null);
            case UNSET, DROPPING_LOOT, GOING_TO_JOBSITE, NO_SPACE, NO_SUPPLIES, COLLECTING_SUPPLIES, IDLE, RELAXING ->
                    null;
        };


        this.world.tryWorking(town, entity, workSpot);
    }

    Map<SmelterAction, WorkSpot<SmelterAction, BlockPos>> listAllWorkspots(
            ServerLevel level,
            @Nullable MCRoom jobSite
    ) {
        if (jobSite == null) {
            return ImmutableMap.of();
        }

        ImmutableMap.Builder<SmelterAction, WorkSpot<SmelterAction, BlockPos>> b = ImmutableMap.builder();
        jobSite.getSpaces().stream()
                .flatMap(space -> InclusiveSpaces.getAllEnclosedPositions(space).stream())
                .forEach(v -> {
                    BlockPos bp = Positions.ToBlock(v, jobSite.yCoord);
                    SmelterAction blockAction = fromBlocks(level, bp);
                    if (blockAction != SmelterAction.UNDEFINED) {
                        b.put(blockAction, new WorkSpot<>(bp, blockAction, 0));
                    }
                });
        return b.build();
    }

    private static SmelterAction fromBlocks(
            ServerLevel level,
            BlockPos bp
    ) {
        if (SmeltingOvenBlock.hasOreToCollect(level, bp)) {
            return SmelterAction.COLLECT_RAW_PRODUCT;
        }
        if (SmeltingOvenBlock.canAcceptWork(level, bp)) {
            return SmelterAction.PROCESSS_ORE;
        }
        if (SmeltingOvenBlock.canAcceptOre(level, bp)) {
            return SmelterAction.INSERT_ORE;
        }
        return SmelterAction.UNDEFINED;
    }

    @Override
    public void initializeStatusFromEntityData(@Nullable String s) {
        SmelterStatus from = SmelterStatus.from(s);
        if (from == SmelterStatus.UNSET) {
            from = SmelterStatus.IDLE;
        }
        this.journal.initializeStatus(from);
        // TODO: Implement String->Status conversion
    }

    @Override
    public String getStatusToSyncToClient() {
        return journal.getStatus().name();
    }

    @Override
    protected Map<SmelterStatus, Boolean> getSupplyItemStatus() {
        // TODO: Support more ores
        Ingredient axe = Ingredient.of(TagsInit.Items.PICKAXES);
        boolean hasOre = journal.getItems().stream().anyMatch(v -> Items.IRON_ORE.equals(v.get().get()));
        boolean hasPickaxe = journal.getItems().stream().anyMatch(v -> axe.test(v.get().toItemStack()));
        return ImmutableMap.of(
                SmelterStatus.WORK_INSERTING_ORE, hasOre,
                SmelterStatus.WORK_PROCESSING_ORE, hasPickaxe
        );
    }

    @Override
    protected BlockPos findProductionSpot(ServerLevel sl) {
        if (workSpot != null) {
            // TODO: Choose a location based on its horizontal orientation
            return workSpot.position.relative(Direction.getRandom(sl.getRandom()));
        }
        return null;
    }

    @Override
    protected BlockPos findJobSite(TownInterface town) {
        @Nullable ServerLevel sl = town.getServerLevel();
        if (sl == null) {
            return null;
        }

        // TODO: Use tags to support more tiers of bakery
        Collection<RoomRecipeMatch<MCRoom>> bakeries = town.getRoomsMatching(WORKSITE_ID);

        Map<SmelterStatus, Boolean> statuses = getSupplyItemStatus();

        // TODO: Sort by distance and choose the closest
        for (RoomRecipeMatch<MCRoom> match : bakeries) {
            for (Map.Entry<BlockPos, Block> blocks : match.getContainedBlocks().entrySet()) {
                SmelterAction workSite = fromBlocks(sl, blocks.getKey());
                boolean shouldGo = switch (workSite) {
                    case COLLECT_RAW_PRODUCT -> statuses.getOrDefault(SmelterStatus.WORK_COLLECTING_RAW_PRODUCT, false);
                    case PROCESSS_ORE -> statuses.getOrDefault(SmelterStatus.WORK_PROCESSING_ORE, false);
                    case INSERT_ORE -> statuses.getOrDefault(SmelterStatus.WORK_INSERTING_ORE, false);
                    case UNDEFINED -> false;
                };
                if (shouldGo) {
                    return blocks.getKey();
                }
            }
        }

        return null;
    }

    @Override
    protected Map<SmelterStatus, ? extends Collection<MCRoom>> roomsNeedingIngredients(TownInterface town) {
        return ImmutableMap.of(
                SmelterStatus.WORK_PROCESSING_ORE, Jobs.roomsWithState(town, SmeltingOvenBlock::canAcceptWork),
                SmelterStatus.WORK_INSERTING_ORE, Jobs.roomsWithState(town, SmeltingOvenBlock::canAcceptOre)
        );
    }

    @Override
    protected BlockPos findNonWorkTarget(
            BlockPos entityBlockPos,
            Vec3 entityPos,
            TownInterface town
    ) {
        return null;
    }

    @Override
    public boolean openScreen(
            ServerPlayer sp,
            VisitorMobEntity e
    ) {
        return Jobs.openInventoryAndStatusScreen(journal.getCapacity(), sp, e);
    }

    @Override
    public TranslatableComponent getJobName() {
        return new TranslatableComponent("jobs.smelter");
    }
}

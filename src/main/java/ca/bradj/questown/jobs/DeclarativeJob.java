package ca.bradj.questown.jobs;

import ca.bradj.questown.QT;
import ca.bradj.questown.blocks.JobBlock;
import ca.bradj.questown.blocks.SmeltingOvenBlock;
import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.integration.minecraft.MCTownItem;
import ca.bradj.questown.jobs.declarative.WorldInteraction;
import ca.bradj.questown.mobs.visitor.VisitorMobEntity;
import ca.bradj.questown.town.interfaces.TownInterface;
import ca.bradj.roomrecipes.adapter.Positions;
import ca.bradj.roomrecipes.adapter.RoomRecipeMatch;
import ca.bradj.roomrecipes.logic.InclusiveSpaces;
import ca.bradj.roomrecipes.serialization.MCRoom;
import com.google.common.collect.ImmutableBiMap;
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

public class DeclarativeJob extends ProductionJob<ProductionStatus, SimpleSnapshot<ProductionStatus, MCHeldItem>, DeclarativeJournal> {

    private final ImmutableBiMap<ProductionStatus, Integer> statusToBlockState;
    private final int maxState;
    private final ImmutableMap<Integer, ImmutableList<Ingredient>> ingredientsRequiredAtStates;

    private static final ImmutableList<MCTownItem> allowedToPickUp = ImmutableList.of(
            MCTownItem.fromMCItemStack(Items.COAL.getDefaultInstance()),
            MCTownItem.fromMCItemStack(Items.IRON_ORE.getDefaultInstance()),
            MCTownItem.fromMCItemStack(Items.RAW_IRON.getDefaultInstance())
    );

    private static final Marker marker = MarkerManager.getMarker("Smelter");
    private final WorldInteraction world;
    private final ResourceLocation workBlockId;
    private Signals signal;
    private WorkSpot<Integer, BlockPos> workSpot;

    public DeclarativeJob(
            UUID ownerUUID,
            int inventoryCapacity,
            ResourceLocation workBlockId,
            int maxState,
            ImmutableMap<Integer, ImmutableList<Ingredient>> ingredientsRequiredAtStates,
            ImmutableMap<Integer, ImmutableList<Ingredient>> toolsRequiredAtStates,
            ImmutableMap<Integer, Integer> workRequiredAtStates,
            ImmutableBiMap<ProductionStatus, Integer> statusToBlockState
    ) {
        super(ownerUUID, inventoryCapacity, allowedToPickUp, buildRecipe(
                ingredientsRequiredAtStates, statusToBlockState
        ), marker);
        this.world = new WorldInteraction(inventory, journal, maxState, ingredientsRequiredAtStates, workRequiredAtStates, toolsRequiredAtStates);
        this.workBlockId = workBlockId;
        this.maxState = maxState;
        this.ingredientsRequiredAtStates = ingredientsRequiredAtStates;
        this.statusToBlockState = statusToBlockState;
    }

    private static <STATUS> RecipeProvider<STATUS> buildRecipe(
            ImmutableMap<Integer, ImmutableList<Ingredient>> ingredientsRequiredAtStates,
            ImmutableMap<STATUS, Integer> statusToBlockState
    ) {
        return s -> {
            ImmutableList.Builder<JobsClean.TestFn<MCTownItem>> bb = ImmutableList.builder();
            ImmutableList<Ingredient> ingrs = ingredientsRequiredAtStates.get(statusToBlockState.get(s));
            if (ingrs != null) {
                ingrs.forEach(v -> bb.add(item -> v.test(item.toItemStack())));
            }
            return bb.build();
        };
    }

    @Override
    protected DeclarativeJournal getInitializedJournal(int inventoryCapacity) {
        return new DeclarativeJournal(
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
            Map<ProductionStatus, ? extends Collection<MCRoom>> roomsNeedingIngredients
    ) {
        this.signal = Signals.fromGameTime(town.getServerLevel().getDayTime());
        JobTownProvider<ProductionStatus, MCRoom> jtp = new JobTownProvider<>() {
            @Override
            public Collection<MCRoom> roomsWithCompletedProduct() {
                return Jobs.roomsWithState(town, SmeltingOvenBlock::hasOreToCollect);
            }

            @Override
            public Map<ProductionStatus, ? extends Collection<MCRoom>> roomsNeedingIngredients() {
                return roomsNeedingIngredients;
            }

            @Override
            public boolean hasSupplies() {
                Map<ProductionStatus, ? extends Collection<MCRoom>> needs = roomsNeedingIngredients();
                return Jobs.townHasSupplies(town, journal, convertToCleanFns(needs));
            }

            @Override
            public boolean hasSpace() {
                return Jobs.townHasSpace(town);
            }
        };
        BlockPos entityBlockPos = entity.blockPosition();
        RoomRecipeMatch<MCRoom> entityCurrentJobSite = Jobs.getEntityCurrentJobSite(town, workBlockId, entityBlockPos);
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
        Map<Integer, WorkSpot<Integer, BlockPos>> workSpots = listAllWorkspots(
                town.getServerLevel(),
                entityCurrentJobSite.room
        );

        ProductionStatus status = getStatus();
        if (!statusToBlockState.containsKey(status)) {
            QT.JOB_LOGGER.warn("Shouldn't try to work when status is {}", status);
            return;
        }

        Integer key = statusToBlockState.get(status);
        this.workSpot = workSpots.getOrDefault(key, null);

        this.world.tryWorking(town, entity, workSpot);
    }

    Map<Integer, WorkSpot<Integer, BlockPos>> listAllWorkspots(
            ServerLevel level,
            @Nullable MCRoom jobSite
    ) {
        if (jobSite == null) {
            return ImmutableMap.of();
        }

        ImmutableMap.Builder<Integer, WorkSpot<Integer, BlockPos>> b = ImmutableMap.builder();
        jobSite.getSpaces().stream()
                .flatMap(space -> InclusiveSpaces.getAllEnclosedPositions(space).stream())
                .forEach(v -> {
                    BlockPos bp = Positions.ToBlock(v, jobSite.yCoord);
                    @Nullable Integer blockAction = JobBlock.getState(level, bp);
                    if (blockAction != null) {
                        b.put(blockAction, new WorkSpot<>(bp, blockAction, 0));
                    }
                });
        return b.build();
    }

    @Override
    public void initializeStatusFromEntityData(@Nullable String s) {
        ProductionStatus from = ProductionStatus.from(s);
        if (from == ProductionStatus.UNSET) {
            from = ProductionStatus.IDLE;
        }
        this.journal.initializeStatus(from);
        // TODO: Implement String->Status conversion
    }

    @Override
    public String getStatusToSyncToClient() {
        return journal.getStatus().name();
    }

    @Override
    protected Map<ProductionStatus, Boolean> getSupplyItemStatus() {
        ImmutableMap.Builder<ProductionStatus, Boolean> b = ImmutableMap.builder();
        ingredientsRequiredAtStates.forEach(
                (state, ingrs) -> {
                    // This only passes if the worker has ALL the ingredients needed for the state
                    boolean has = ingrs.stream().allMatch(
                            v -> journal.getItems().stream().anyMatch(z -> v.test(z.get().toItemStack()))
                    );
                    ImmutableBiMap<Integer, ProductionStatus> state2Status = statusToBlockState.inverse();
                    b.put(state2Status.get(state), has);
                }
        );
        return b.build();
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
        Collection<RoomRecipeMatch<MCRoom>> bakeries = town.getRoomsMatching(workBlockId);

        Map<ProductionStatus, Boolean> statusItems = getSupplyItemStatus();

        // TODO: Sort by distance and choose the closest
        for (RoomRecipeMatch<MCRoom> match : bakeries) {
            for (Map.Entry<BlockPos, Block> blocks : match.getContainedBlocks().entrySet()) {
                @Nullable Integer blockState = JobBlock.getState(sl, blocks.getKey());
                if (blockState == null) {
                    continue;
                }
                Map<Integer, ProductionStatus> state2Status = statusToBlockState.inverse();
                boolean shouldGo = statusItems.getOrDefault(state2Status.get(blockState), false);
                if (shouldGo) {
                    return blocks.getKey();
                }
            }
        }

        return null;
    }

    @Override
    protected Map<ProductionStatus, ? extends Collection<MCRoom>> roomsNeedingIngredients(TownInterface town) {
        return ImmutableMap.of(
                ProductionStatus.WORKING_ON_PRODUCTION, Jobs.roomsWithState(town, (sl, bp) -> !JobBlock.hasFinishedProduct(sl, bp)),
                ProductionStatus.EXTRACTING_PRODUCT, Jobs.roomsWithState(town, JobBlock::hasFinishedProduct)
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

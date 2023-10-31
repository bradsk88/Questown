package ca.bradj.questown.jobs;

import ca.bradj.questown.QT;
import ca.bradj.questown.blocks.JobBlock;
import ca.bradj.questown.blocks.OreProcessingBlock;
import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.integration.minecraft.MCTownItem;
import ca.bradj.questown.jobs.declarative.ProductionJournal;
import ca.bradj.questown.jobs.declarative.WorldInteraction;
import ca.bradj.questown.jobs.production.ProductionJob;
import ca.bradj.questown.jobs.production.ProductionStatus;
import ca.bradj.questown.mobs.visitor.VisitorMobEntity;
import ca.bradj.questown.town.interfaces.TownInterface;
import ca.bradj.roomrecipes.adapter.Positions;
import ca.bradj.roomrecipes.adapter.RoomRecipeMatch;
import ca.bradj.roomrecipes.logic.InclusiveSpaces;
import ca.bradj.roomrecipes.serialization.MCRoom;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
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

import java.util.*;
import java.util.function.BiConsumer;

public class DeclarativeJob extends ProductionJob<ProductionStatus, SimpleSnapshot<ProductionStatus, MCHeldItem>, ProductionJournal<MCTownItem, MCHeldItem>> {

    private final ImmutableMap<Integer, Ingredient> ingredientsRequiredAtStates;
    private final ImmutableMap<Integer, Ingredient> toolsRequiredAtStates;

    private static final ImmutableList<MCTownItem> allowedToPickUp = ImmutableList.of(
            MCTownItem.fromMCItemStack(Items.COAL.getDefaultInstance()),
            MCTownItem.fromMCItemStack(Items.IRON_ORE.getDefaultInstance()),
            MCTownItem.fromMCItemStack(Items.RAW_IRON.getDefaultInstance())
    );

    private static final Marker marker = MarkerManager.getMarker("Smelter");
    private final WorldInteraction world;
    private final ResourceLocation workRoomId;
    private Signals signal;
    private WorkSpot<Integer, BlockPos> workSpot;
    private TranslatableComponent name;

    public DeclarativeJob(
            UUID ownerUUID,
            int inventoryCapacity,
            TranslatableComponent name,
            ResourceLocation workRoomId,
            int maxState,
            ImmutableMap<Integer, Ingredient> ingredientsRequiredAtStates,
            ImmutableMap<Integer, Integer> ingredientsQtyRequiredAtStates,
            ImmutableMap<Integer, Ingredient> toolsRequiredAtStates,
            ImmutableMap<Integer, Integer> workRequiredAtStates
    ) {
        super(
                ownerUUID, inventoryCapacity, allowedToPickUp, buildRecipe(
                        ingredientsRequiredAtStates, toolsRequiredAtStates
                ), marker, new IProductionStatusFactory<>() {
                    @Override
                    public ProductionStatus fromJobBlockState(int s) {
                        return ProductionStatus.fromJobBlockStatus(s);
                    }

                    @Override
                    public ProductionStatus droppingLoot() {
                        return ProductionStatus.FACTORY.droppingLoot();
                    }

                    @Override
                    public ProductionStatus noSpace() {
                        return ProductionStatus.FACTORY.noSpace();
                    }

                    @Override
                    public ProductionStatus goingToJobSite() {
                        return ProductionStatus.FACTORY.goingToJobSite();
                    }

                    @Override
                    public ProductionStatus noSupplies() {
                        return ProductionStatus.FACTORY.noSupplies();
                    }

                    @Override
                    public ProductionStatus collectingSupplies() {
                        return ProductionStatus.FACTORY.collectingSupplies();
                    }

                    @Override
                    public ProductionStatus idle() {
                        return ProductionStatus.FACTORY.idle();
                    }

                    @Override
                    public ProductionStatus extractingProduct() {
                        return ProductionStatus.FACTORY.extractingProduct();
                    }

                    @Override
                    public ProductionStatus relaxing() {
                        return ProductionStatus.FACTORY.relaxing();
                    }
                }
        );
        this.name = name;
        this.world = new WorldInteraction(
                inventory,
                journal,
                maxState,
                ingredientsRequiredAtStates,
                workRequiredAtStates,
                toolsRequiredAtStates
        );
        this.workRoomId = workRoomId;
        this.ingredientsRequiredAtStates = ingredientsRequiredAtStates;
        this.toolsRequiredAtStates = toolsRequiredAtStates;
    }

    private static RecipeProvider buildRecipe(
            ImmutableMap<Integer, Ingredient> ingredientsRequiredAtStates,
            ImmutableMap<Integer, Ingredient> toolsRequiredAtStates
    ) {
        return s -> {
            ImmutableList.Builder<JobsClean.TestFn<MCTownItem>> bb = ImmutableList.builder();
            Ingredient ingr = ingredientsRequiredAtStates.get(s);
            if (ingr != null) {
                bb.add(item -> ingr.test(item.toItemStack()));
            }
            Ingredient tool = toolsRequiredAtStates.get(s);
            if (tool != null) {
                bb.add(item -> tool.test(item.toItemStack()));
            }
            return bb.build();
        };
    }

    @Override
    protected ProductionJournal<MCTownItem, MCHeldItem> getInitializedJournal(
            int inventoryCapacity,
            IStatusFactory<ProductionStatus> sFac
    ) {
        return new ProductionJournal<>(
                () -> this.signal,
                inventoryCapacity,
                MCHeldItem::Air,
                sFac
        );
    }

    @Override
    protected void tick(
            TownInterface town,
            LivingEntity entity,
            Direction facingPos,
            Map<Integer, ? extends Collection<MCRoom>> roomsNeedingIngredients,
            IProductionStatusFactory<ProductionStatus> statusFactory
    ) {
        this.signal = Signals.fromGameTime(town.getServerLevel().getDayTime());
        JobTownProvider<MCRoom> jtp = new JobTownProvider<>() {
            @Override
            public Collection<MCRoom> roomsWithCompletedProduct() {
                return Jobs.roomsWithState(town, workRoomId, OreProcessingBlock::hasOreToCollect);
            }

            @Override
            public Map<Integer, ? extends Collection<MCRoom>> roomsNeedingIngredientsByState() {
                return roomsNeedingIngredients;
            }

            @Override
            public boolean hasSupplies() {
                Map<Integer, ? extends Collection<MCRoom>> needs = roomsNeedingIngredientsByState();
                return Jobs.townHasSupplies(town, journal, convertToCleanFns(needs));
            }

            @Override
            public boolean hasSpace() {
                return Jobs.townHasSpace(town);
            }
        };
        BlockPos entityBlockPos = entity.blockPosition();
        RoomRecipeMatch<MCRoom> entityCurrentJobSite = Jobs.getEntityCurrentJobSite(town, workRoomId, entityBlockPos);
        EntityLocStateProvider<MCRoom> elp = new EntityLocStateProvider<>() {
            @Override
            public @Nullable RoomRecipeMatch<MCRoom> getEntityCurrentJobSite() {

                return entityCurrentJobSite;
            }
        };
        this.journal.tick(jtp, elp, super.defaultEntityInvProvider(), statusFactory);

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
        if (status == null || status.isUnset() || !status.isWorkingOnProduction()) {
            QT.JOB_LOGGER.warn("Shouldn't try to work when status is {}", status);
            return;
        }

        this.workSpot = workSpots.getOrDefault(status.getProductionState(), null);

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
        ProductionStatus from;
        try {
            from = ProductionStatus.from(s);
        } catch (NumberFormatException nfe) {
            QT.JOB_LOGGER.error("Ignoring exception: {}", nfe.getMessage());
            from = ProductionStatus.FACTORY.idle();
        }
        if (from.isUnset()) {
            from = ProductionStatus.FACTORY.idle();
        }
        this.journal.initializeStatus(from);
    }

    @Override
    public String getStatusToSyncToClient() {
        return journal.getStatus().name();
    }

    @Override
    protected Map<Integer, Boolean> getSupplyItemStatus() {
        HashMap<Integer, Boolean> b = new HashMap<>();
        BiConsumer<Integer, Ingredient> fn = (state, ingr) -> {
            if (ingr == null) {
                if (!b.containsKey(state)) {
                    b.put(state, false);
                }
                return;
            }

            // The check passes if the worker has ALL the ingredients needed for the state
            boolean has = journal.getItems().stream().anyMatch(z -> ingr.test(z.get().toItemStack()));
            if (!b.getOrDefault(state, false)) {
                b.put(state, has);
            }
        };
        ingredientsRequiredAtStates.forEach(fn);
        toolsRequiredAtStates.forEach(fn);
        return ImmutableMap.copyOf(b);
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
        Collection<RoomRecipeMatch<MCRoom>> bakeries = town.getRoomsMatching(workRoomId);

        Map<Integer, Boolean> statusItems = getSupplyItemStatus();

        // TODO: Sort by distance and choose the closest
        for (RoomRecipeMatch<MCRoom> match : bakeries) {
            for (Map.Entry<BlockPos, Block> blocks : match.getContainedBlocks().entrySet()) {
                @Nullable Integer blockState = JobBlock.getState(sl, blocks.getKey());
                if (blockState == null) {
                    continue;
                }
                boolean shouldGo = statusItems.getOrDefault(blockState, false);
                if (shouldGo) {
                    return blocks.getKey();
                }
            }
        }

        return null;
    }

    @Override
    protected Map<Integer, ? extends Collection<MCRoom>> roomsNeedingIngredientsOrTools(TownInterface town) {
        HashMap<Integer, List<MCRoom>> b = new HashMap<>();
        ingredientsRequiredAtStates.forEach((state, ingrs) -> {
            if (ingrs.isEmpty()) {
                b.put(state, new ArrayList<>());
                return;
            }
            b.put(state, Lists.newArrayList(Jobs.roomsWithState(
                    town, workRoomId, (sl, bp) -> state.equals(JobBlock.getState(sl, bp))
            )));
        });
        toolsRequiredAtStates.forEach((state, ingrs) -> {
            if (ingrs.isEmpty()) {
                if (!b.containsKey(state)) {
                    b.put(state, new ArrayList<>());
                }
                return;
            }
            b.get(state).addAll((Jobs.roomsWithState(
                    town, workRoomId, (sl, bp) -> state.equals(JobBlock.getState(sl, bp))
            )));
        });
        return ImmutableMap.copyOf(b);
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
        return this.name;
    }
}

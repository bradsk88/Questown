package ca.bradj.questown.mobs.visitor;

import ca.bradj.questown.Questown;
import ca.bradj.questown.core.advancements.VisitorTrigger;
import ca.bradj.questown.core.init.AdvancementsInit;
import ca.bradj.questown.core.init.EntitiesInit;
import ca.bradj.questown.gui.UIQuest;
import ca.bradj.questown.gui.VisitorQuestsContainer;
import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.jobs.*;
import ca.bradj.questown.town.TownFlagBlockEntity;
import ca.bradj.questown.town.interfaces.TownInterface;
import ca.bradj.questown.town.quests.MCQuest;
import ca.bradj.questown.town.quests.Quest;
import ca.bradj.questown.town.special.SpecialQuests;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Dynamic;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.protocol.game.DebugPackets;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.behavior.*;
import net.minecraft.world.entity.ai.goal.OpenDoorGoal;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.sensing.Sensor;
import net.minecraft.world.entity.ai.sensing.SensorType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.entity.schedule.Schedule;
import net.minecraft.world.entity.schedule.ScheduleBuilder;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

import java.util.*;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static org.lwjgl.glfw.GLFW.GLFW_CURSOR;
import static org.lwjgl.glfw.GLFW.GLFW_CURSOR_NORMAL;

public class VisitorMobEntity extends PathfinderMob {

    public static final String DEFAULT_SCHEDULE_ID = "visitor_default_schedule";
    public static final Schedule DEFAULT_SCHEDULE = new ScheduleBuilder(new Schedule())
            .changeActivityAt(10, Activity.IDLE)
            .changeActivityAt(12500, Activity.REST)
            .build();
    public static final Map<MemoryModuleType<GlobalPos>, BiPredicate<VisitorMobEntity, PoiType>> POI_MEMORIES = ImmutableMap.of(
            MemoryModuleType.HOME, (p_35493_, p_35494_) -> p_35494_ == PoiType.HOME
    );
    private static final EntityDataAccessor<Boolean> visible = SynchedEntityData.defineId(
            VisitorMobEntity.class, EntityDataSerializers.BOOLEAN
    );
    private static final EntityDataAccessor<String> status = SynchedEntityData.defineId(
            VisitorMobEntity.class, EntityDataSerializers.STRING
    );

    private static final String NBT_TOWN_X = "town_x";
    private static final String NBT_TOWN_Y = "town_y";
    private static final String NBT_TOWN_Z = "town_z";
    private static final ImmutableList<SensorType<? extends Sensor<? super Villager>>> SENSOR_TYPES = ImmutableList.of(
            SensorType.NEAREST_BED
    );

    Job<MCHeldItem, ? extends Snapshot> job = getJob();
    private static final int inventoryCapacity = 6;
    private final AABB defaultBB;

    // TODO: Make this abstract or injectable
    @NotNull
    private GathererJob getJob() {
        return new GathererJob(level.isClientSide() ? null : (ServerLevel) level, inventoryCapacity, uuid);
    }

    boolean sitting = true;
    TownInterface town;
    private BlockPos wanderTarget;
    private boolean initializedItems;
    private List<ChangeListener> changeListeners = new ArrayList<>();
    private boolean initialized;

    public VisitorMobEntity(
            EntityType<? extends PathfinderMob> ownType,
            Level level,
            TownInterface town
    ) {
        super(ownType, level);
        this.defaultBB = getBoundingBox();
        this.town = town;
        if (town != null) {
            initBrain();
        }
        // Technically this also gets us item updates because item changes cause status to go back to IDLE
        // But this is admittedly a bit fragile.
        this.job.addStatusListener((newStatus) -> this.changeListeners.forEach(ChangeListener::Changed));
    }

    public VisitorMobEntity(
            ServerLevel level,
            TownInterface town
    ) {
        this(EntitiesInit.VISITOR.get(), level, town);
    }

    public static AttributeSupplier setAttributes() {
        return PathfinderMob
                .createMobAttributes()
                .add(Attributes.FOLLOW_RANGE, 48.0)
                .build();
    }

    public static ImmutableList<Pair<Integer, ? extends Behavior<? super VisitorMobEntity>>> getCorePackage(
    ) {
        return ImmutableList.of(
                Pair.of(0, new Swim(0.8F)),
//                Pair.of(0, new InteractWithDoor()),
                Pair.of(0, new LookAtTargetSink(45, 90)),
                Pair.of(0, new WakeUp()),
                Pair.of(1, new MoveToTargetSink()),
                Pair.of(4, new Admire(200)),
                Pair.of(5, new CoerceWalk()),
                Pair.of(9, new ValidateBed()),
                Pair.of(10, new FindOpenBed())
//                Pair.of(10, new AcquirePoi(PoiType.HOME, MemoryModuleType.HOME, false, Optional.of((byte) 14)))
        );
    }

    public static ImmutableList<Pair<Integer, ? extends Behavior<? super VisitorMobEntity>>> getRestPackage(
    ) {
        Behavior<? super VisitorMobEntity> walkTarget = new SetWalkTargetFromBlockMemory(
                MemoryModuleType.HOME, 0.5f, 1, 150, 1200
        );
        return ImmutableList.of(
                Pair.of(
                        2,
                        walkTarget
                ),
//                Pair.of(3, new ValidateNearbyPoi(PoiType.HOME, MemoryModuleType.HOME)),
                Pair.of(3, new SleepInBed()),
                Pair.of(
                        5,
                        new RunOne<>(
                                ImmutableMap.of(MemoryModuleType.HOME, MemoryStatus.VALUE_ABSENT),
                                ImmutableList.of(
                                        Pair.of(new TownWalk(0.4f), 1),
                                        Pair.of(new SetClosestHomeAsWalkTarget(0.5f), 1),
                                        Pair.of(new InsideBrownianWalk(0.5f), 4),
                                        Pair.of(new DoNothing(20, 40), 2)
                                )
                        )
                ),
                Pair.of(99, new UpdateActivityFromSchedule())
        );
    }

    public static ImmutableList<Pair<Integer, ? extends Behavior<? super VisitorMobEntity>>> getIdlePackage(
            VisitorMobEntity entity
    ) {
        ImmutableList.Builder<Pair<Integer, ? extends Behavior<? super VisitorMobEntity>>> b = ImmutableList.builder();
//        b.add(Pair.of(2, new DoNothing(30, 60)));
        b.add(Pair.of(3, new TownWalk(0.3f)));
        b.add(Pair.of(10, new TownWalk(0.40f)));
        b.add(Pair.of(99, new UpdateActivityFromSchedule()));
        return b.build();
    }

    private static void openDialogScreen(
            ServerPlayer sp,
            Collection<UIQuest> quests,
            VisitorQuestsContainer.VisitorContext ctx
    ) {
        NetworkHooks.openGui(sp, new MenuProvider() {
            @Override
            public @NotNull Component getDisplayName() {
                return TextComponent.EMPTY;
            }

            @Override
            public @NotNull AbstractContainerMenu createMenu(
                    int windowId,
                    @NotNull Inventory inv,
                    @NotNull Player p
            ) {
                return new VisitorQuestsContainer(windowId, quests, ctx);
            }
        }, data -> {
            UIQuest.Serializer ser = new UIQuest.Serializer();
            data.writeInt(quests.size());
            data.writeCollection(quests, (buf, recipe) -> {
                ResourceLocation id;
                if (recipe == null) {
                    id = SpecialQuests.BROKEN;
                    recipe = new UIQuest(SpecialQuests.SPECIAL_QUESTS.get(id), Quest.QuestStatus.ACTIVE, null);
                } else {
                    id = recipe.getRecipeId();
                }
                buf.writeResourceLocation(id);
                ser.toNetwork(buf, recipe);
            });
            data.writeBoolean(ctx.isFirstVillager);
            data.writeInt(ctx.finishedQuests);
            data.writeInt(ctx.unfinishedQuests);
        });
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(visible, true);
        this.entityData.define(status, GathererJournal.Status.IDLE.name());
    }

    @NotNull
    private CompoundTag buildInitialSlotStatusesTag() {
        CompoundTag tag = new CompoundTag();
        ListTag statuses = new ListTag();
        for (int i = 0; i < inventoryCapacity; i++) {
            statuses.add(IntTag.valueOf(0));
        }
        tag.put("slots", statuses);
        return tag;
    }

    @Override
    public boolean removeWhenFarAway(double p_21542_) {
        // See keepAlive
        return false;
    }

    @Override
    public void tick() {
        super.tick();

        if (isInWall()) {
            Vec3 nudged = position().add(-1.0 + random.nextDouble(2.0), 0, -1.0 + random.nextDouble(2.0));
            Questown.LOGGER.debug("Villager is stuck in wall. Nudging to {}", nudged);
            moveTo(nudged);
        }

        if (job.getStatus() == GathererJournal.Status.UNSET) {
            GathererJournal.Status s = getStatus();
            if (s == GathererJournal.Status.UNSET) {
                s = GathererJournal.Status.IDLE;
            }
            job.initializeStatus(s);
        }
        job.tick(town, blockPosition());
        if (!level.isClientSide()) {
            boolean vis = !job.shouldDisappear(town, position());
            this.entityData.set(visible, vis);
            entityData.set(status, job.getStatus().name());
        }
    }

    @Override
    public void remove(RemovalReason p_146834_) {
        super.remove(p_146834_);
        if (p_146834_.equals(RemovalReason.KILLED)) {
            for (int i = 0; i < getInventory().getContainerSize(); i++) {
                level.addFreshEntity(new ItemEntity(level, position().x, position().y, position().z, getInventory().getItem(i)));
            }
        }
    }

    @Override
    public boolean isColliding(
            BlockPos p_20040_,
            BlockState p_20041_
    ) {
        if (this.job.shouldBeNoClip(town, blockPosition())) {
            return false;
        }
        return super.isColliding(p_20040_, p_20041_);
    }

    @Override
    public boolean canBeCollidedWith() {
        if (this.job.shouldBeNoClip(town, blockPosition())) {
            return false;
        }
        return super.canBeCollidedWith();
    }

    @Override
    public boolean canCollideWith(Entity p_20303_) {
        if (this.job.shouldBeNoClip(town, blockPosition())) {
            return false;
        }
        return super.canCollideWith(p_20303_);
    }

    @Override
    public void push(double p_20286_, double p_20287_, double p_20288_) {
        if (this.job.shouldBeNoClip(town, blockPosition())) {
            return;
        }
        super.push(p_20286_, p_20287_, p_20288_);
    }

    @Override
    protected void pushEntities() {
        if (this.job.shouldBeNoClip(town, blockPosition())) {
            return;
        }
        super.pushEntities();
    }

    @Override
    public boolean hurt(DamageSource p_21016_, float p_21017_) {
        if (this.job.shouldBeNoClip(town, blockPosition())) {
            return false;
        }
        return super.hurt(p_21016_, p_21017_);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag p_21484_) {
        super.addAdditionalSaveData(p_21484_);
        ListTag items = new ListTag();
        Jobs.getItems(job).forEach(v -> items.add(v.serializeNBT()));
        p_21484_.put("items", items);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag p_21450_) {
        super.readAdditionalSaveData(p_21450_);
        ListTag items = p_21450_.getList("items", Tag.TAG_COMPOUND);
        List<MCHeldItem> itemz = items
                .stream()
                .map(v -> ItemStack.of((CompoundTag) v))
                .map(MCHeldItem::fromMCItemStack)
                .toList();
        job.initializeItems(itemz);
        entityData.set(status, p_21450_.getString("status"));
    }

    private void initBrain() {
        this.getBrain().setMemory(MemoryModuleType.LAST_SLEPT, Optional.empty());
        this.getBrain().setMemory(MemoryModuleType.LAST_WOKEN, Optional.empty());
        this.getBrain().setMemory(MemoryModuleType.WALK_TARGET, Optional.empty());
        this.getBrain().setMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE, Optional.empty());
        this.getBrain().setMemory(MemoryModuleType.PATH, Optional.empty());
    }

    @Override
    public boolean shouldRender(
            double p_20296_,
            double p_20297_,
            double p_20298_
    ) {
        Boolean isVisible = this.entityData.get(visible);
        if (!isVisible) {
            return false;
        }
        return super.shouldRender(p_20296_, p_20297_, p_20298_);
    }

    @Override
    public boolean save(CompoundTag p_20224_) {
        if (town == null) {
            Questown.LOGGER.error("Town is null. This is a bug.");
            return super.save(p_20224_);
        }
        BlockPos bp = this.town.getTownFlagBasePos();
        p_20224_.putInt(NBT_TOWN_X, bp.getX());
        p_20224_.putInt(NBT_TOWN_Y, bp.getY());
        p_20224_.putInt(NBT_TOWN_Z, bp.getZ());
        return super.save(p_20224_);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains(NBT_TOWN_X) && tag.contains(NBT_TOWN_Y) && tag.contains(NBT_TOWN_Z)) {
            BlockPos bp = new BlockPos(tag.getInt(NBT_TOWN_X), tag.getInt(NBT_TOWN_Y), tag.getInt(NBT_TOWN_Z));
            if (this.level instanceof ServerLevel sl) {
                BlockEntity entity = sl.getBlockEntity(bp);
                if (!(entity instanceof TownFlagBlockEntity flag)) {
                    Questown.LOGGER.error("Entity at {} was not a TownFlag", bp);
                    return;
                }
                this.town = flag;
                flag.assumeStateFromTown(this, sl);
                this.initBrain();
            }
        }
    }

    @Override
    protected PathNavigation createNavigation(Level p_21480_) {
        GroundPathNavigation gpn = new GroundPathNavigation(this, p_21480_);
        gpn.setCanOpenDoors(true);
        gpn.setCanPassDoors(true);
        return gpn;
    }

    @Override
    public float getPathfindingMalus(BlockPathTypes p_21440_) {
        return super.getPathfindingMalus(p_21440_);
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        this.goalSelector.addGoal(2, new OpenDoorGoal(this, true));
        // TODO: Make a behaviour that only runs in the day
//        this.goalSelector.addGoal(4, new TownWalk(this, 2, 0.5f));
    }

    @Override
    protected Brain.Provider<?> brainProvider() {
        return Brain.provider(ImmutableList.of(
                MemoryModuleType.HOME,
                MemoryModuleType.LAST_SLEPT,
                MemoryModuleType.LAST_WOKEN,
                MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE,
                MemoryModuleType.WALK_TARGET,
                MemoryModuleType.PATH,
                MemoryModuleType.NEAREST_BED,
                MemoryModuleType.INTERACTION_TARGET,
                MemoryModuleType.DISABLE_WALK_TO_ADMIRE_ITEM
        ), SENSOR_TYPES);
    }

    @Override
    protected Brain<?> makeBrain(Dynamic<?> p_21069_) {
        Brain<VisitorMobEntity> brain = (Brain<VisitorMobEntity>) super.makeBrain(p_21069_);
        brain.setSchedule(VisitorMobEntity.DEFAULT_SCHEDULE);
        brain.addActivity(Activity.IDLE, getIdlePackage(this));
        brain.addActivity(Activity.REST, getRestPackage());
        brain.addActivity(Activity.CORE, getCorePackage());
        brain.setCoreActivities(ImmutableSet.of(Activity.CORE));
        brain.updateActivityFromSchedule(this.level.getDayTime(), this.level.getGameTime());
        return brain;
    }

    @Override
    public void die(DamageSource p_21014_) {
        this.releasePoi(MemoryModuleType.HOME);
        super.die(p_21014_);
    }

    @Override
    public void startSleeping(BlockPos p_21141_) {
        super.startSleeping(p_21141_);
        this.brain.setMemory(MemoryModuleType.LAST_SLEPT, this.level.getGameTime());
        this.brain.eraseMemory(MemoryModuleType.WALK_TARGET);
        this.brain.eraseMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
    }

    @Override
    public void stopSleeping() {
        super.stopSleeping();
        this.brain.setMemory(MemoryModuleType.LAST_WOKEN, this.level.getGameTime());
        this.setHealth(this.getMaxHealth());
    }

    @Override
    protected void customServerAiStep() {
        this.level.getProfiler().push(String.format("%s_%s", Questown.MODID, "visitorBrain"));
        Brain<VisitorMobEntity> brain1 = (Brain<VisitorMobEntity>) this.getBrain();
        brain1.tick((ServerLevel) this.level, this);
        this.level.getProfiler().pop();
        super.customServerAiStep();
    }

    public boolean isSitting() {
        return this.sitting;
    }

    public BlockPos getWanderTarget() {
        if (town == null) {
            this.kill();
            return new BlockPos(0, 0, 0);
        }
        return this.wanderTarget;
    }

    public void setWanderTarget(BlockPos blockPos) {
        this.wanderTarget = blockPos;
        if (blockPos == null) {
            this.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        } else {
            this.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(blockPos, 0.3f, 0));
        }
    }

    public BlockPos newWanderTarget() {
        if (town == null) {
            this.kill();
            return null;
        }
        BlockPos target = job.getTarget(blockPosition(), town);
        if (target != null) {
            this.setWanderTarget(target);
        } else {
            this.setWanderTarget(town.getRandomWanderTarget());
        }
        return this.getWanderTarget();
    }

    public void releasePoi(MemoryModuleType<GlobalPos> p_35429_) {
        if (this.level instanceof ServerLevel) {
            MinecraftServer minecraftserver = ((ServerLevel) this.level).getServer();
            this.brain.getMemory(p_35429_).ifPresent((p_186306_) -> {
                ServerLevel serverlevel = minecraftserver.getLevel(p_186306_.dimension());
                if (serverlevel != null) {
                    PoiManager poimanager = serverlevel.getPoiManager();
                    Optional<PoiType> optional = poimanager.getType(p_186306_.pos());
                    BiPredicate<VisitorMobEntity, PoiType> bipredicate = POI_MEMORIES.get(p_35429_);
                    if (optional.isPresent() && bipredicate.test(this, optional.get())) {
                        poimanager.release(p_186306_.pos());
                        DebugPackets.sendPoiTicketCountPacket(serverlevel, p_186306_.pos());
                    }

                }
            });
        }
    }

    @Override
    public InteractionResult interactAt(
            Player player,
            Vec3 p_19981_,
            InteractionHand p_19982_
    ) {
        boolean isClientSide = player.level.isClientSide();
        if (!(player instanceof ServerPlayer sp)) {
            return InteractionResult.sidedSuccess(isClientSide);
        }

        if (!this.entityData.get(visible)) {
            return InteractionResult.PASS;
        }

        Collection<MCQuest> q4v = town.getQuestsForVillager(getUUID());
        Collection<UIQuest> quests = UIQuest.fromLevel(level, q4v);

        AdvancementsInit.VISITOR_TRIGGER.trigger(
                sp, VisitorTrigger.Triggers.FirstVisitor
        );

        Predicate<MCQuest> isComplete = Quest::isComplete;
        Set<MCQuest> finishedQuests = q4v
                .stream()
                .filter(isComplete)
                .collect(Collectors.toSet());
        Set<MCQuest> unfinishedQuests = q4v
                .stream()
                .filter(isComplete.negate())
                .collect(Collectors.toSet());

        VisitorQuestsContainer.VisitorContext ctx = new VisitorQuestsContainer.VisitorContext(
                town.getVillagers().stream().filter(Objects::nonNull).toList().size() == 1,
                finishedQuests.size(),
                unfinishedQuests.size()
        );
        if (!job.openScreen(sp, this)) {
            openDialogScreen(sp, quests, ctx);
        }

        return InteractionResult.sidedSuccess(isClientSide);
    }

    public Container getInventory() {
        return job.getInventory();
    }

    public GathererJournal.Status getStatus() {
        return GathererJournal.Status.from(entityData.get(status));
    }

//    // If all else fails, we can use this
//    @Override
//    public boolean removeWhenFarAway(double p_21542_) {
//        return false;
//    }

    public void setStatusListener(StatusListener l) {
        job.addStatusListener(l);
    }

    public Snapshot getJobJournalSnapshot() {
        return job.getJournalSnapshot();
    }

    public void initialize(
            UUID uuid,
            double xPos,
            double yPos,
            double zPos,
            Snapshot journal
    ) {
        // TODO: Implement generic job initialization
        if (journal instanceof GathererJournal.Snapshot && job instanceof GathererJob j) {
            j.initialize((GathererJournal.Snapshot<MCHeldItem>) journal);
            job = j;
        } else {
            Questown.LOGGER.error("Initialization not implemented");
        }
        this.setPos(xPos, yPos, zPos);
        this.setUUID(uuid);
        this.initialized = true;
    }

    public static boolean debuggerReleaseControl() {
        GLFW.glfwSetInputMode(Minecraft.getInstance().getWindow().getWindow(), GLFW_CURSOR, GLFW_CURSOR_NORMAL);
        return true;
    }

    public boolean isInitialized() {
        return this.initialized;
    }

    public List<Boolean> getSlotLocks() {
        return this.job.getSlotLockStatuses();
    }

    public DataSlot getLockSlot(int i) {
        return job.getLockSlot(i);
    }

    public interface ChangeListener {
        void Changed();

    }

    public void addChangeListener(ChangeListener cl) {
        this.changeListeners.add(cl);
    }

    // TODO: Remove this
    @Override
    public boolean hurt(
            DamageSource p_21016_,
            float p_21017_
    ) {
        convertToCook();
        return super.hurt(p_21016_, p_21017_);
    }

    // TODO: Generalize
    public void convertToCook() {
        if (level.isClientSide()) {
            return;
        }
        Job<MCHeldItem, ? extends Snapshot> job1 = new FarmerJob(
                (ServerLevel) level,
                6
        );
        this.job = job1;
    }

}

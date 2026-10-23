package ca.bradj.questown.mobs.visitor;

import ca.bradj.questown.InventoryFullStrategy;
import ca.bradj.questown.QT;
import ca.bradj.questown.Questown;
import ca.bradj.questown.commands.DebugLogArgument;
import ca.bradj.questown.core.Config;
import ca.bradj.questown.core.VillagerUUID;
import ca.bradj.questown.core.advancements.VisitorTrigger;
import ca.bradj.questown.core.init.AdvancementsInit;
import ca.bradj.questown.core.init.EntitiesInit;
import ca.bradj.questown.gui.VillagerStats;
import ca.bradj.questown.gui.VillagerStatsMenu;
import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.jobs.*;
import ca.bradj.questown.jobs.gatherer.GathererUnmappedNoToolWorkQtrDay;
import ca.bradj.questown.mc.Compat;
import ca.bradj.questown.mc.Util;
import ca.bradj.questown.town.PoseInPlace;
import ca.bradj.questown.town.VillagerStatsData;
import ca.bradj.questown.town.entity.TownFlagBlockEntity;
import ca.bradj.questown.town.entity.TownVillagerMobs;
import ca.bradj.questown.town.entity.TownieRuntime;
import ca.bradj.questown.town.interfaces.TownInterface;
import ca.bradj.questown.town.interfaces.TownieBody;
import ca.bradj.questown.town.quests.MCQuest;
import ca.bradj.questown.town.quests.MCReward;
import ca.bradj.questown.town.quests.Quest;
import ca.bradj.roomrecipes.serialization.MCRoom;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Dynamic;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.network.protocol.game.DebugPackets;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.behavior.*;
import net.minecraft.world.entity.ai.goal.OpenDoorGoal;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.sensing.Sensor;
import net.minecraft.world.entity.ai.sensing.SensorType;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.entity.schedule.Schedule;
import net.minecraft.world.entity.schedule.ScheduleBuilder;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import ca.bradj.questown.gui.Ingredients;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.BiPredicate;
import java.util.function.Consumer;

import static net.minecraft.world.entity.Pose.SLEEPING;

/**
 * A townie's body: it walks (the vanilla brain, navigation, doors and gates), shows what its town
 * flag pushes to it (visibility, status, job name, held items, need bubble) and reports what
 * happens to it (unloaded, killed, gave up reaching). The townie itself (its job, carried items,
 * status, need and every decision) is a {@link TownieRuntime} held by the flag, which makes the
 * townie's decisions in its own tick. Keep townie state and decisions off this class: the getters
 * that look like townie state here only forward to the runtime. See ADR-0016 and
 * {@code town/entity/COMPLEX.md} "Townie runtime and body".
 */
@SuppressWarnings("NullableProblems")
public class VisitorMobEntity extends PathfinderMob implements VillagerStats, TownieBody {

    public static final String DEFAULT_SCHEDULE_ID = "visitor_default_schedule";
    public static final Schedule DEFAULT_SCHEDULE = new ScheduleBuilder(new Schedule())
            .changeActivityAt(10, Activity.IDLE)
            .changeActivityAt(12500, Activity.REST)
            .build();
    public static final Map<MemoryModuleType<GlobalPos>, BiPredicate<VisitorMobEntity, Holder<PoiType>>> POI_MEMORIES = ImmutableMap.of(
            MemoryModuleType.HOME, (p_35493_, p_35494_) -> p_35494_.is(PoiTypes.HOME)
    );
    private static final EntityDataAccessor<Boolean> visible = SynchedEntityData.defineId(
            VisitorMobEntity.class, EntityDataSerializers.BOOLEAN
    );
    private static final EntityDataAccessor<Boolean> hasBOP = SynchedEntityData.defineId(
            VisitorMobEntity.class, EntityDataSerializers.BOOLEAN
    );
    private static final EntityDataAccessor<ItemStack> heldItem = SynchedEntityData.defineId(
            VisitorMobEntity.class, EntityDataSerializers.ITEM_STACK
    );
    private static final EntityDataAccessor<ItemStack> lastHeldItem = SynchedEntityData.defineId(
            VisitorMobEntity.class, EntityDataSerializers.ITEM_STACK
    );
    /**
     * The townie's current unmet need, as a {@link TownieNeed} name, or empty for "nothing wrong".
     * Synched because the bubble that renders it is client-side; a String is used for the same
     * reason the helper chicken's bubble fields are — it serializes with no custom serializer.
     */
    private static final EntityDataAccessor<String> need = SynchedEntityData.defineId(
            VisitorMobEntity.class, EntityDataSerializers.STRING
    );
    /**
     * When {@link #need} is {@link TownieNeed#UNMET_ITEM}, the missing ingredient as its
     * {@link ca.bradj.questown.gui.Ingredients#toString} form, so a client bubble can name and
     * show it (the Inventory screen's "needs" row pattern). Empty for any other need.
     */
    private static final EntityDataAccessor<String> unmetItem = SynchedEntityData.defineId(
            VisitorMobEntity.class, EntityDataSerializers.STRING
    );

    private static final String NBT_TOWN_X = "town_x";
    private static final String NBT_TOWN_Y = "town_y";
    private static final String NBT_TOWN_Z = "town_z";
    private static final ImmutableList<SensorType<? extends Sensor<? super Villager>>> SENSOR_TYPES = ImmutableList.of(
            SensorType.NEAREST_BED
    );
    private static final float slowWalkSpeed = 0.25f;
    private static final float walkSpeed = 0.3f;
    private static final float runSpeed = 0.4f;
    private final ArrayList<Integer> tickTimes = new ArrayList<>();
    private final ArrayList<Integer> targetTimes = new ArrayList<>();
    boolean sitting = true;
    TownInterface town;
    /** The townie this body stands for: its state and decisions, held by the town flag. Null until bound. */
    private @Nullable TownieRuntime runtime;
    private final List<Consumer<VillagerSleptEvent>> sleepListeners = new ArrayList<>();
    private Signals.DayTime lastBedTime = new Signals.DayTime(Signals.NIGHT_START_TICK);
    private BlockPos wanderTarget;
    /** See {@link TownieBody#lastTickedAt()}. */
    private long lastTickedAt = -1;

    public VisitorMobEntity(
            EntityType<? extends PathfinderMob> ownType,
            Level level,
            TownInterface town
    ) {
        super(ownType, level);
        this.setCanPickUpLoot(true);
        this.town = town;
        if (town != null) {
            initBrain();
            setArrowCount((int) (town.getVillagerHandle()
                                     .getDamageTicksLeft(uuid) / Compat.configGet(Config.DAMAGE_TICKS).get()));
        }
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
                .add(Attributes.FOLLOW_RANGE, 100)
                .build();
    }

    public static ImmutableList<Pair<Integer, ? extends BehaviorControl<? super VisitorMobEntity>>> getCorePackage(
    ) {
        return ImmutableList.of(
                Pair.of(0, new Swim(0.8F)),
                Pair.of(0, new LookAtWalkTarget()),
                Pair.of(0, new LookAtTargetSink(45, 90)),
                Pair.of(0, WakeUp.create()),
                Pair.of(1, new MoveToTownTargetSink()),
                Pair.of(4, new Admire(100)),
                Pair.of(5, new CoerceWalk()),
                Pair.of(9, new ValidateBed())
        );
    }

    public static ImmutableList<Pair<Integer, ? extends BehaviorControl<? super VisitorMobEntity>>> getRestPackage(
    ) {
        BehaviorControl<? super VisitorMobEntity> walkTarget = new SetWalkTargetFromBlockMemory(
                MemoryModuleType.HOME, 0.5f, 1, 150, 1200
        );

        return ImmutableList.of(
                Pair.of(1, walkTarget),
                Pair.of(4, new SleepInBed()),
                Pair.of(99, UpdateActivityFromSchedule.create())
        );
    }

    public static ImmutableList<Pair<Integer, ? extends BehaviorControl<? super VisitorMobEntity>>> getIdlePackage(
            VisitorMobEntity entity
    ) {
        ImmutableList.Builder<Pair<Integer, ? extends BehaviorControl<? super VisitorMobEntity>>> b = ImmutableList.builder();
        b.add(Pair.of(3, new LookAtWalkTarget()));
        b.add(Pair.of(99, UpdateActivityFromSchedule.create()));
        return b.build();
    }

    /** The townie this body stands for, or null until the entity is bound to its town. */
    public @Nullable TownieRuntime getRuntime() {
        return runtime;
    }

    public @Nullable BlockPos getLookTarget() {
        return runtime == null ? null : runtime.getLook();
    }

    public boolean tryGiveItem(
            MCHeldItem v,
            InventoryFullStrategy inventoryFullStrategy
    ) {
        if (runtime == null) {
            QT.VILLAGER_LOGGER.error("Townie {} is not bound to its town; cannot take {}", getUUID(), v);
            return false;
        }
        return runtime.tryGiveItem(v, inventoryFullStrategy);
    }

    public void addSleepListener(Consumer<VillagerSleptEvent> l) {
        this.sleepListeners.add(l);
    }

    public boolean hasBlockOfProgress() {
        Boolean hasBOP = this.entityData.get(VisitorMobEntity.hasBOP);
        return hasBOP;
    }

    @SuppressWarnings("removal")
    public @Nullable VillagerUUID getVUID() {
        return VillagerUUID.from(getUUID());
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(visible, true);
        builder.define(hasBOP, true);
        builder.define(heldItem, ItemStack.EMPTY);
        builder.define(lastHeldItem, ItemStack.EMPTY);
        builder.define(need, TownieNeed.NONE.name());
        builder.define(unmetItem, "");
    }

    /**
     * Server-only: the townie needs the player's help with something. The need is the townie's,
     * kept by its runtime, which shows it on this body; see {@link TownieRuntime#setNeed}.
     */
    public void setNeed(TownieNeed newNeed) {
        if (runtime != null) {
            runtime.setNeed(newNeed);
        }
    }

    /** The townie's need: its runtime's on the server, what this body shows on the client. */
    public TownieNeed getNeed() {
        if (runtime != null) {
            return runtime.getNeed();
        }
        return TownieNeed.fromName(this.entityData.get(need));
    }

    /** Server-only: the item the {@code UNMET_ITEM} need is about, cleared by {@link #setNeed}. */
    public void setUnmetItem(String itemKey) {
        if (runtime != null) {
            runtime.setUnmetItem(itemKey);
        }
    }

    /**
     * The item this townie is blocked on (its {@link TownieNeed#UNMET_ITEM} key), or null for any
     * other need or when the key does not resolve. Read by the need bubble, the inventory needs
     * row and the status tooltip.
     */
    public @Nullable Ingredient getUnmetItemIngredient() {
        if (getNeed() != TownieNeed.UNMET_ITEM) {
            return null;
        }
        String key = getUnmetItem();
        if (key.isEmpty()) {
            return null;
        }
        try {
            return Ingredients.fromString(key);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** The UNMET_ITEM need's item: its runtime's on the server, what this body shows on the client. */
    public String getUnmetItem() {
        if (runtime != null) {
            return runtime.getUnmetItem();
        }
        return this.entityData.get(unmetItem);
    }

    @Override
    public void showNeed(
            TownieNeed townieNeed,
            String item
    ) {
        this.entityData.set(need, townieNeed.name());
        this.entityData.set(unmetItem, item);
    }

    /** The brain gave up walking to its target: see {@link TownieRuntime#gaveUpReaching}. */
    public void reportGaveUpReaching() {
        if (runtime != null) {
            runtime.gaveUpReaching();
        }
    }

    /** The brain can reach its targets again: see {@link TownieRuntime#canReachAgain}. */
    public void reportCanReachAgain() {
        if (runtime != null) {
            runtime.canReachAgain();
        }
    }

    @Override
    public boolean removeWhenFarAway(double p_21542_) {
        // See keepAlive
        return false;
    }

    @Override
    public void baseTick() {
        super.baseTick();
    }

    @Override
    public void tick() {
        super.tick();

        if (isSleeping()) {
            return; // TODO: Confirm this is ok
        }

        if (!(level() instanceof ServerLevel sl)) {
            return;
        }

        if (runtime == null) {
            return;
        }

        if (!town.isInitialized()) {
            return;
        }

        // See TownieRuntime#tick: the flag decides only for a body that ticked this game tick.
        lastTickedAt = sl.getGameTime();
        entityData.set(hasBOP, town.getVillagerHandle().hasBlockOfProgress(getUUID()));

        if (runtime.isTickFrozen()) {
            return;
        }

        long start = System.currentTimeMillis();
        villagerTick(sl);
        long end = System.currentTimeMillis();

        spawnIdleParticlesIfNeeded(sl);

        tickTimes.add((int) (end - start));

        Integer rate = Compat.configGet(Config.TICK_SAMPLING_RATE).get();
        if (rate != 0 && tickTimes.size() > rate) {
            town.getDebugLogger(QT.VILLAGER_LOGGER, DebugLogArgument.SAMPLING).log(
                    "VME Average tick length: {}",
                    tickTimes.stream()
                             .mapToInt(Integer::intValue)
                             .average()
            );
            tickTimes.clear();
        }
    }


    private void spawnIdleParticlesIfNeeded(ServerLevel sl) {
        if (sl.getGameTime() % 40 != 0) {
            return;
        }
        IStatus<?> s = getStatusForServer();
        if (s == null) {
            return;
        }
        if (!s.name().equals("NO_SUPPLIES")) {
            return;
        }
        addParticlesAroundSelf(sl, ParticleTypes.ANGRY_VILLAGER);
    }

    protected void addParticlesAroundSelf(ServerLevel sl, ParticleOptions p_35288_) {
        double d0 = this.random.nextGaussian() * 0.02D;
        double d1 = this.random.nextGaussian() * 0.02D;
        double d2 = this.random.nextGaussian() * 0.02D;
        sl.sendParticles(p_35288_, this.getRandomX(1.0D), this.getRandomY() + 1.0D, this.getRandomZ(1.0D), 2, 0, d0, d1, d2);
    }

    /**
     * The body's own tick: sense who is looking, show hunger, get unstuck from walls, stay known to
     * the town, open gates. The townie's decisions are the flag's: {@link TownieRuntime}.
     */
    private void villagerTick(ServerLevel sl) {
        detectNearbyDistractions(sl);
        town.getVillagerHandle().getLookTarget(getVUID()).ifPresentOrElse(
                lookControl::setLookAt,
                this::setLookFromBrain
        );

        boolean starving = town.getVillagerHandle().isStarving(getVUID());
        setPose(starving ? Pose.CROUCHING : Pose.STANDING);
        if (entityData.get(visible) && starving && Compat.getRandomInt(sl, 10) == 0) {
            addParticlesAroundSelf(sl, ParticleTypes.EFFECT);
        }

        if (isInWall()) {
            Vec3 nudged = position().add(-1.0 + (random.nextDouble() * 2.0), 0, -1.0 + (random.nextDouble() * 2.0));
            town.getDebugLogger(QT.VILLAGER_LOGGER, DebugLogArgument.VILLAGER_NAVIGATION).log(
                    "Villager is stuck in wall. Nudging to {}", nudged
            );
            moveTo(nudged);
        }

        town.getVillagerHandle().validateEntity(this);

        this.useNearbyGates();
    }

    @Override
    public long lastTickedAt() {
        return lastTickedAt;
    }

    private void setLookFromBrain() {
        brain.getMemory(MemoryModuleType.WALK_TARGET).ifPresent(
                e -> lookControl.setLookAt(e.getTarget().currentPosition())
        );
    }

    @Override
    public void takePose(PoseInPlace pose) {
        switch (pose.pose()) {
            case SLEEPING -> getInBed(pose.place());
            default -> getOutOfBed();
        }
    }

    @Override
    public void swingMainHand() {
        swing(InteractionHand.MAIN_HAND);
    }

    @Override
    public void wearTool(ItemStack tool) {
        tool.hurtAndBreak(1, level().getRandom(), this, () -> {});
    }

    @Override
    public void dropItem(ItemStack stack) {
        level().addFreshEntity(new ItemEntity(level(), getX(), getY(), getZ(), stack));
    }

    @Override
    public Optional<BlockPos> getWalkTarget() {
        return getBrain().getMemory(MemoryModuleType.WALK_TARGET)
                         .map(t -> t.getTarget().currentBlockPosition());
    }

    @Override
    public void clearWalkTarget() {
        getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
    }

    @Override
    public void setVisible(boolean visible) {
        entityData.set(VisitorMobEntity.visible, visible);
    }

    @Override
    public void showMainHandItem(ItemStack item) {
        entityData.set(heldItem, item);
    }

    @Override
    public void showOffhandItem(ItemStack item) {
        entityData.set(lastHeldItem, item);
    }

    private void detectNearbyDistractions(ServerLevel sl) {
        List<Player> ps = level().getNearbyPlayers(
                TargetingConditions.forNonCombat(),
                this,
                this.getBoundingBox().inflate(16.0D, 64.0D, 16.0D)
        );
        for (Player p : ps) {
            if (isLookingAtMe(p)) {
                Long dur = Compat.configGet(Config.VILLAGER_DISTRACT_DURATION).get();
                Long cooldown = Compat.configGet(Config.VILLAGER_DISTRACT_COOLDOWN).get();
                long until = Util.getTick(sl) + dur;
                town.getVillagerHandle().setLookTarget(getVUID(), p, until, until + cooldown);
                return;
            }
        }
    }

    private boolean isLookingAtMe(Player p) {
        if (p.isCrouching()) {
            return false;
        }

        // if player is a few blocks away, don't consider them looking
        double v = p.distanceToSqr(this);
        if (v > 10) {
            return false;
        }

        Vec3 vec3 = p.getViewVector(1.0F).normalize();
        Vec3 vec31 = new Vec3(this.getX() - p.getX(), this.getEyeY() - p.getEyeY(), this.getZ() - p.getZ());
        double d0 = vec31.length();
        vec31 = vec31.normalize();
        double d1 = vec3.dot(vec31);
        return d1 > 0.95D - 0.025D / d0 && p.hasLineOfSight(this);
    }

    private void useNearbyGates() {
        if (getBrain().getMemory(MemoryModuleType.WALK_TARGET)
                      .isPresent()) {
            BlockPos on = blockPosition();
            BlockState bs = level().getBlockState(on);
            if (bs.getBlock() instanceof FarmBlock) {
                on = on.above();
                bs = level().getBlockState(on);
            }
            if (bs.getBlock() instanceof FenceGateBlock) {
                bs = bs.setValue(FenceGateBlock.OPEN, true);
                level().setBlock(on, bs, 10);
            }
            Direction dir = getDirection();
            BlockPos front = on.relative(dir);
            BlockState fbs = level().getBlockState(front);
            if (fbs.getBlock() instanceof FenceGateBlock) {
                fbs = fbs.setValue(FenceGateBlock.OPEN, true);
                level().setBlock(front, fbs, 10);
            }
            front = on.relative(dir.getCounterClockWise());
            fbs = level().getBlockState(front);
            if (fbs.getBlock() instanceof FenceGateBlock) {
                fbs = fbs.setValue(FenceGateBlock.OPEN, true);
                level().setBlock(front, fbs, 10);
            }
            front = on.relative(dir.getClockWise());
            fbs = level().getBlockState(front);
            if (fbs.getBlock() instanceof FenceGateBlock) {
                fbs = fbs.setValue(FenceGateBlock.OPEN, true);
                level().setBlock(front, fbs, 10);
            }
            BlockPos behind = on.relative(getDirection().getOpposite());
            BlockState bbs = level().getBlockState(behind);
            if (bbs.getBlock() instanceof FenceGateBlock) {
                bbs = bbs.setValue(FenceGateBlock.OPEN, false);
                level().setBlock(behind, bbs, 10);
            }
        }
    }

    /**
     * A chunk unload stops tracking the entity without calling {@link #remove} (only KILLED
     * unregistered), so the town kept the unloaded copy and the reloaded entity registered a
     * second time - counted twice (2026-10-17, village/day1 reload step). Unregister on unload
     * (the loader's tracking-end hook, Forge and NeoForge alike); the entity re-registers when
     * it loads.
     */
    @Override
    public void onRemovedFromWorld() {
        super.onRemovedFromWorld();
        if (level().isClientSide() || !(town instanceof TownFlagBlockEntity flag)) {
            return;
        }
        RemovalReason reason = getRemovalReason();
        if (reason == RemovalReason.UNLOADED_TO_CHUNK || reason == RemovalReason.UNLOADED_WITH_PLAYER) {
            flag.townieBodyUnloaded(this);
        }
    }

    @Override
    public void remove(RemovalReason p_146834_) {
        super.remove(p_146834_);
        if (p_146834_.equals(RemovalReason.KILLED)) {
            QT.VILLAGER_LOGGER.info(
                    "Townie {} died: damageSource={} directEntity={} pos=({}, {}, {})",
                    this.getVUID(),
                    getLastDamageSource(),
                    getLastDamageSource() != null ? getLastDamageSource().getDirectEntity() : null,
                    position().x,
                    position().y,
                    position().z
            );
            if (town instanceof TownFlagBlockEntity flag) {
                flag.townieKilled(this);
            }
        }
    }

    @Override
    public boolean isColliding(
            BlockPos p_20040_,
            BlockState p_20041_
    ) {
        if (isNoClip()) {
            return false;
        }
        return super.isColliding(p_20040_, p_20041_);
    }

    @Override
    public boolean canBeCollidedWith() {
        if (isNoClip()) {
            return false;
        }
        return super.canBeCollidedWith();
    }

    @Override
    public boolean canCollideWith(Entity p_20303_) {
        if (isNoClip()) {
            return false;
        }
        return super.canCollideWith(p_20303_);
    }

    @Override
    public void push(
            double p_20286_,
            double p_20287_,
            double p_20288_
    ) {
        if (isNoClip()) {
            return;
        }
        super.push(p_20286_, p_20287_, p_20288_);
    }

    /** The townie's job lets it pass through blocks and entities (server-side only; never on the client). */
    private boolean isNoClip() {
        return runtime != null && runtime.shouldBeNoClip(blockPosition());
    }

    @Override
    protected void pushEntities() {
        if (isNoClip()) {
            return;
        }
        List<Entity> list = this.level().getEntities(this, this.getBoundingBox(), EntitySelector.pushableBy(this));
        list = list.stream().filter(v -> !(getClass().isInstance(v) || v instanceof Player)).toList();
        // Copied from superclass
        if (!list.isEmpty()) {
            int i = this.level().getGameRules().getInt(GameRules.RULE_MAX_ENTITY_CRAMMING);
            if (i > 0 && list.size() > i - 1 && this.random.nextInt(4) == 0) {
                int j = 0;

                for (Entity entity : list) {
                    if (!entity.isPassenger()) {
                        ++j;
                    }
                }

                if (j > i - 1) {
                    this.hurt(this.level().damageSources().cramming(), 6.0F);
                }
            }

            for (Entity entity : list) {
                this.doPush(entity);
            }
        }
    }

    private void initBrain() {
        this.getBrain()
            .setMemory(MemoryModuleType.LAST_SLEPT, Optional.empty());
        this.getBrain()
            .setMemory(MemoryModuleType.LAST_WOKEN, Optional.empty());
        this.getBrain()
            .setMemory(MemoryModuleType.WALK_TARGET, Optional.empty());
        this.getBrain()
            .setMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE, Optional.empty());
        this.getBrain()
            .setMemory(MemoryModuleType.PATH, Optional.empty());
    }

    @Override
    public ItemStack getMainHandItem() {
        return getEntityData().get(heldItem);
    }

    @Override
    public ItemStack getOffhandItem() {
        return getEntityData().get(lastHeldItem);
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
            QT.VILLAGER_LOGGER.error("Town is null. This is a bug.");
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
            if (this.level() instanceof ServerLevel sl) {
                BlockEntity entity = sl.getBlockEntity(bp);
                if (!(entity instanceof TownFlagBlockEntity flag)) {
                    QT.VILLAGER_LOGGER.error("Entity at {} was not a TownFlag", bp);
                    return;
                }
                this.town = flag;
                // FIXME: This can fail with "Town has not been initialized on TownVillagerHandle"
                //  This should either be retried multiple times, or this initialization
                //  should be handled by the town flag
                TownVillagerMobs.assumeStateFromTown(flag, this);
                this.initBrain();
            }
        }
    }

    @Override
    protected PathNavigation createNavigation(Level p_21480_) {
        return new VillagerPathNavigation(this, p_21480_);
    }

    // No door malus override: vanilla's walk evaluator treats any NEGATIVE malus as
    // impassable (WalkNodeEvaluator `>= 0.0F` gates), so the 2026-10-15 "door preference"
    // discount (DOOR_OPEN 0.0 - 0.5) made OPEN doors unwalkable and sent villagers over
    // walls and onto beds/chests (2026-10-17 playtest; see mobs/visitor/COMPLEX.md).

    @Override
    protected void registerGoals() {
        super.registerGoals();
        this.goalSelector.addGoal(2, new OpenDoorGoal(this, true));
        this.goalSelector.addGoal(2, new OpenGateGoal(this, true));
    }

    @Override
    protected Brain.Provider<?> brainProvider() {
        return Brain.provider(
                ImmutableList.of(
                        MemoryModuleType.HOME,
                        MemoryModuleType.LAST_SLEPT,
                        MemoryModuleType.LAST_WOKEN,
                        MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE,
                        MemoryModuleType.WALK_TARGET,
                        MemoryModuleType.PATH,
                        MemoryModuleType.NEAREST_BED,
                        MemoryModuleType.INTERACTION_TARGET,
                        MemoryModuleType.DISABLE_WALK_TO_ADMIRE_ITEM
                ), SENSOR_TYPES
        );
    }

    @Override
    protected Brain<?> makeBrain(Dynamic<?> p_21069_) {
        //noinspection unchecked
        Brain<VisitorMobEntity> brain = (Brain<VisitorMobEntity>) super.makeBrain(p_21069_);
        brain.setSchedule(VisitorMobEntity.DEFAULT_SCHEDULE);
        brain.addActivity(Activity.IDLE, getIdlePackage(this));
        brain.addActivity(Activity.REST, getRestPackage());
        brain.addActivity(Activity.CORE, getCorePackage());
        brain.setCoreActivities(ImmutableSet.of(Activity.CORE));
        brain.updateActivityFromSchedule(this.level().getDayTime(), this.level().getGameTime());
        return brain;
    }

    @Override
    public void die(DamageSource p_21014_) {
        this.releasePoi(MemoryModuleType.HOME);
        super.die(p_21014_);
    }

    @Override
    public void startSleeping(BlockPos p_21141_) {
        getInBed(p_21141_);
        this.setSleepingPos(p_21141_);
        this.brain.setMemory(MemoryModuleType.LAST_SLEPT, this.level().getGameTime());
        this.brain.eraseMemory(MemoryModuleType.WALK_TARGET);
        this.brain.eraseMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
        this.lastBedTime = Util.getDayTime(level());
    }

    private void getInBed(BlockPos p_21141_) {
        brain.eraseMemory(MemoryModuleType.WALK_TARGET);
        brain.eraseMemory(MemoryModuleType.PATH);
        if (this.isPassenger()) {
            this.stopRiding();
        }

        BlockState blockstate = this.level().getBlockState(p_21141_);
        if (blockstate.getBlock() instanceof ca.bradj.questown.blocks.HospitalBedBlock) {
            level().setBlock(p_21141_, blockstate.setValue(ca.bradj.questown.blocks.HospitalBedBlock.OCCUPIED, Boolean.TRUE), 3);
        }

        this.setPose(SLEEPING);
        this.setPosToBed(p_21141_);
        this.setDeltaMovement(Vec3.ZERO);
        this.hasImpulse = true;
    }

    private void setPosToBed(BlockPos p_21081_) {
        this.setPos(
                (double) p_21081_.getX() + 0.5D,
                (double) p_21081_.getY() + 0.6875D,
                (double) p_21081_.getZ() + 0.5D
        );
    }

    @Override
    public void stopSleeping() {
        Optional<BlockPos> sleepingPos = getOutOfBed();
        long curTime = this.level().getGameTime();
        this.brain.setMemory(MemoryModuleType.LAST_WOKEN, curTime);

        sleepingPos.ifPresent(
                bed -> {
                    long nightSleepTicks = lastBedTime.ticksBeforeMidnight();
                    long ticksSlept = Util.getDayTime(level()).dayTime() + nightSleepTicks;
                    sleepListeners.forEach(l -> l.accept(new VillagerSleptEvent(ticksSlept, bed)));
                }
        );

        this.setHealth(this.getMaxHealth());
    }

    private @NotNull Optional<BlockPos> getOutOfBed() {
        Optional<BlockPos> sleepingPos = this.getSleepingPos();
        sleepingPos.filter((p_147227_) -> this.level().hasChunkAt(p_147227_)).ifPresent((p_147228_) -> {
            BlockState blockstate = this.level().getBlockState(p_147228_);
            if (blockstate.getBlock() instanceof ca.bradj.questown.blocks.HospitalBedBlock) {
                this.level().setBlock(p_147228_, blockstate.setValue(ca.bradj.questown.blocks.HospitalBedBlock.OCCUPIED, Boolean.FALSE), 3);
                Vec3 vec31 = BedBlock.findStandUpPosition(this.getType(), this.level(), p_147228_, this.getDirection(), this.getYRot())
                                     .orElseGet(() -> {
                                         BlockPos blockpos = p_147228_.above();
                                         return new Vec3(
                                                 (double) blockpos.getX() + 0.5D,
                                                 (double) blockpos.getY() + 0.1D,
                                                 (double) blockpos.getZ() + 0.5D
                                         );
                                     });
                Vec3 vec32 = Vec3.atBottomCenterOf(p_147228_).subtract(vec31).normalize();
                float f = (float) Mth.wrapDegrees(Mth.atan2(
                        vec32.z,
                        vec32.x
                ) * (double) (180F / (float) Math.PI) - 90.0D);
                this.setPos(vec31.x, vec31.y, vec31.z);
                this.setYRot(f);
                this.setXRot(0.0F);
            }

        });
        Vec3 vec3 = this.position();
        this.setPose(Pose.STANDING);
        this.setPos(vec3.x, vec3.y, vec3.z);
        this.clearSleepingPos();
        return sleepingPos;
    }

    @Override
    public void aiStep() {
        this.updateSwingTime();
        super.aiStep();
    }

    @Override
    protected void customServerAiStep() {
        this.level().getProfiler()
                  .push(String.format("%s_%s", Questown.MODID, "visitorBrain"));
        //noinspection unchecked
        Brain<VisitorMobEntity> brain1 = (Brain<VisitorMobEntity>) this.getBrain();
        brain1.tick((ServerLevel) this.level(), this);
        this.level().getProfiler()
                  .pop();
        super.customServerAiStep();

        adjustSpeed(brain1);

    }

    private void adjustSpeed(Brain<VisitorMobEntity> brain1) {
        if (!brain1.hasMemoryValue(MemoryModuleType.PATH)) {
            return;
        }
        Optional<Path> path = brain1.getMemory(MemoryModuleType.PATH);
        if (path.isEmpty()) {
            return;
        }
        Path vPath = path.get();
        int nodeCount = vPath.getNodeCount();
        if (nodeCount < 10) {
            return;
        }
        if (runtime != null && runtime.getGlobalSpecialRules().contains(SpecialRules.SLOW_WALK)) {
            getNavigation().setSpeedModifier(slowWalkSpeed);
            return;
        }

        if (vPath.getNextNodeIndex() > nodeCount * 0.75f) {
            getNavigation().setSpeedModifier(walkSpeed);
            return;
        }
        getNavigation().setSpeedModifier(runSpeed);
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

    @Override
    public void setWalkTarget(BlockPos blockPos) {
        this.wanderTarget = blockPos;
        if (blockPos == null) {
            this.getBrain()
                .eraseMemory(MemoryModuleType.WALK_TARGET);
        } else {
            this.getBrain()
                .setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(blockPos, 0.3f, 0));
        }
    }

    public BlockPos newWanderTarget() {
        if (town == null) {
            this.kill();
            return null;
        }
        long start = System.currentTimeMillis();
        BlockPos target = runtime == null ? null : runtime.targetFor(this);
        long end = System.currentTimeMillis();

        targetTimes.add((int) (end - start));

        Integer rate = Compat.configGet(Config.TICK_SAMPLING_RATE).get();
        if (rate != 0 && targetTimes.size() > rate) {
            town.getDebugLogger(QT.PROFILE_LOGGER, DebugLogArgument.SAMPLING).log(
                    "VME Average target acquisition length: {}",
                    targetTimes.stream()
                               .mapToInt(Integer::intValue)
                               .average()
            );
            targetTimes.clear();
        }

        if (target != null) {
            this.setWalkTarget(target);
        } else {

            this.setWalkTarget(town.getRandomWanderTarget(blockPosition()));
        }
        return this.getWanderTarget();
    }

    public void releasePoi(MemoryModuleType<GlobalPos> p_35429_) {
        if (this.level() instanceof ServerLevel) {
            MinecraftServer minecraftserver = ((ServerLevel) this.level()).getServer();
            this.brain.getMemory(p_35429_)
                      .ifPresent((p_186306_) -> {
                          ServerLevel serverlevel = minecraftserver.getLevel(p_186306_.dimension());
                          if (serverlevel != null) {
                              PoiManager poimanager = serverlevel.getPoiManager();
                              Optional<Holder<PoiType>> optional = poimanager.getType(p_186306_.pos());
                              BiPredicate<VisitorMobEntity, Holder<PoiType>> bipredicate = POI_MEMORIES.get(p_35429_);
                              if (optional.isPresent() && bipredicate.test(this, optional.get())) {
                                  poimanager.release(p_186306_.pos());
                                  DebugPackets.sendPoiTicketCountPacket(serverlevel, p_186306_.pos());
                              }

                          }
                      });
        }
    }

    @Override
    protected void jumpFromGround() {
        BlockState onBlock = level().getBlockState(blockPosition());
        if (runtime != null && !runtime.isJumpingAllowed(onBlock)) {
            // Jumping destroys crops. Don't do it when we don't have to
            return;
        }
        super.jumpFromGround();
    }

    @Override
    public InteractionResult interactAt(
            Player player,
            Vec3 p_19981_,
            InteractionHand hand
    ) {
        boolean isClientSide = player.level().isClientSide();
        if (!(player instanceof ServerPlayer sp)) {
            return InteractionResult.sidedSuccess(isClientSide);
        }

        if (!this.entityData.get(visible)) {
            return InteractionResult.PASS;
        }

        AdvancementsInit.VISITOR_TRIGGER.trigger(sp, VisitorTrigger.Triggers.FirstVisitor);
        Jobs.openInventoryAndStatusScreen(sp, this);

        return InteractionResult.sidedSuccess(isClientSide);
    }

    public Container getInventory() {
        if (runtime == null) {
            return new SimpleContainer(0);
        }
        return runtime.getInventory();
    }

    public @Nullable IStatus<?> getStatusForServer() {
        return runtime == null ? null : runtime.getStatus();
    }

    /** See {@link TownieRuntime#getWorkProgressDebug()}. */
    public @Nullable String getWorkProgressDebug() {
        return runtime == null ? null : runtime.getWorkProgressDebug();
    }

    /** See {@link TownieRuntime#getLeaverReturn()}. */
    public @Nullable LeaverReturn getLeaverReturn() {
        return runtime == null ? null : runtime.getLeaverReturn();
    }

    public void addStatusListener(StatusListener l) {
        if (runtime != null) {
            runtime.addStatusListener(l);
        }
    }

    public @Nullable ImmutableSnapshot<MCHeldItem, ?> getJobJournalSnapshot() {
        return runtime == null ? null : runtime.getJournalSnapshot();
    }

    /**
     * Binds this body to the townie it stands for: the town flag builds the townie's runtime from
     * {@code journal} and this entity becomes its body.
     */
    @SuppressWarnings("rawtypes")
    public void initialize(
            TownInterface town,
            UUID uuid,
            double xPos,
            double yPos,
            double zPos,
            Snapshot journal
    ) {
        this.town = town;
        if (!(town instanceof TownFlagBlockEntity flag)) {
            QT.VILLAGER_LOGGER.error("Townie {} can only be bound to a town flag, not {}", uuid, town);
            return;
        }
        this.runtime = flag.bindTownie(uuid, journal, this);
        this.setPos(xPos, yPos, zPos);
        this.setUUID(uuid);
    }

    /** Binds this reloaded body to the townie it stands for, which stayed in town while it was unloaded. */
    public void reattachTo(
            TownFlagBlockEntity flag,
            TownieRuntime townie
    ) {
        this.town = flag;
        this.runtime = townie;
    }

    public boolean isInitialized() {
        return runtime != null;
    }

    public List<Boolean> getSlotLocks() {
        return runtime == null ? List.of() : runtime.getSlotLocks();
    }

    public @Nullable DataSlot getLockSlot(int i) {
        return runtime == null ? null : runtime.getLockSlot(i);
    }

    public @Nullable JobName getJobName() {
        return runtime == null ? null : runtime.getJobName();
    }

    public Collection<? extends Quest<ResourceLocation, MCRoom>> getQuests() {
        return town.getQuestHandle().getQuestsForVillager(uuid);
    }

    public List<AbstractMap.SimpleEntry<MCQuest, MCReward>> getQuestsWithRewards() {
        return town.getQuestHandle().getQuestsWithRewardsForVillager(uuid);
    }

    @Override
    public boolean hurt(
            DamageSource p_21016_,
            float p_21017_
    ) {
        if (isNoClip()) {
            return false;
        }

        // The town's campfire is furniture: vanilla's CampfireBlock.entityInside burns any
        // LivingEntity inside a lit campfire block to death in about a second (1 fire damage
        // per tick, no armor, no burn time). Pathfinding already hard-blocks lit campfires for
        // townies (VillagerPathNavigation), so this covers the routes it cannot: a campfire
        // placed under a standing townie, or a stuck-unstick shove into the block. Fire from
        // any other source (lava, real flames) still hurts.
        if ((p_21016_.is(DamageTypes.IN_FIRE) || p_21016_.is(DamageTypes.ON_FIRE)) && !level().isClientSide() && inOrOnLitCampfire()) {
            return false;
        }

        return super.hurt(p_21016_, p_21017_);
    }

    /** The feet block or the block below the feet is a lit campfire: the two ways a townie's
     *  body can end up inside the campfire block's space. */
    private boolean inOrOnLitCampfire() {
        BlockPos feet = blockPosition();
        return CampfireBlock.isLitCampfire(level().getBlockState(feet))
                || CampfireBlock.isLitCampfire(level().getBlockState(feet.below()));
    }

    @Override
    protected void pickUpItem(ItemEntity p_21471_) {
        // TODO: Implement picking up work-related items
//        ItemStack itemstack = p_21471_.getItem();
//        int taken = 0;
//        for (int i = 0; i < itemstack.getCount(); i++) {
//            if (this.job.addToEmptySlot(MCHeldItem.fromTown(itemstack))) {
//                taken++;
//                continue;
//            }
//            break;
//        }
//        if (taken == 0) {
//            return;
//        }
//        if (taken < itemstack.getCount()) {
//            itemstack.shrink(taken);
//            this.spawnAtLocation(itemstack);
//            p_21471_.discard();
//        }
//        if (taken > 0) {
//            this.onItemPickup(p_21471_);
//            this.take(p_21471_, itemstack.getCount());
//            p_21471_.discard();
//        }
    }


    @Override
    public String toString() {
        return String.format("%s [%s]", super.toString(), getUUID());
    }

    /**
     * The townie's current job; a body not yet bound to its town (load-time registration runs
     * first, and a client entity is never bound) reports the job every townie starts with.
     */
    public JobID getJobId() {
        if (runtime == null) {
            return GathererUnmappedNoToolWorkQtrDay.ID;
        }
        return runtime.getJobId();
    }

    /**
     * Whether the current job lets the hungry->dining short-circuit snap this townie straight
     * to a dining work. Server-only.
     */
    public boolean canInstantlySwitchToEating() {
        return runtime != null && runtime.canInstantlySwitchToEating();
    }

    public BlockPos getFlagPos() {
        return town.getTownFlagBasePos();
    }

    public TownInterface getTown() {
        return town;
    }

    public void freeze(int ticks) {
        if (runtime != null) {
            runtime.freeze(ticks);
        }
    }

    public boolean isTickFrozen() {
        return runtime != null && runtime.isTickFrozen();
    }

    public void removeStatusListener(StatusListener inventoryAndStatusMenu) {
        if (runtime != null) {
            runtime.removeStatusListener(inventoryAndStatusMenu);
        }
    }

    public void addWantedIngredientsListener(Consumer<ImmutableList<Ingredient>> l) {
        if (runtime != null) {
            runtime.addWantedIngredientsListener(l);
        }
    }

    public void removeWantedIngredientsListener(Consumer<ImmutableList<Ingredient>> l) {
        if (runtime != null) {
            runtime.removeWantedIngredientsListener(l);
        }
    }

    public void addStatsListener(VillagerStatsMenu villagerStatsMenu) {
        if (town == null || town.getServerLevel() == null) {
            return;
        }
        town.getVillagerHandle()
            .addStatsListener(getUUID(), villagerStatsMenu);
    }


    public void removeStatsListener(Consumer<VillagerStatsData> villagerStatsMenu) {
        if (town == null || town.getServerLevel() == null) {
            return;
        }
        town.getVillagerHandle()
            .removeStatsListener(getUUID(), villagerStatsMenu);
    }

    public boolean shouldStandStill() {
        return runtime != null && runtime.shouldStandStill(blockPosition());
    }

    public boolean canStopWorkingAtAnyTime() {
        return runtime != null && runtime.canStopWorkingAtAnyTime();
    }

}

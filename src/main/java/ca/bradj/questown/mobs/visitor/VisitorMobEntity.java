package ca.bradj.questown.mobs.visitor;

import ca.bradj.questown.Questown;
import ca.bradj.questown.core.advancements.VisitorTrigger;
import ca.bradj.questown.core.init.AdvancementsInit;
import ca.bradj.questown.gui.UIQuest;
import ca.bradj.questown.gui.VisitorQuestsContainer;
import ca.bradj.questown.integration.minecraft.GathererStatuses;
import ca.bradj.questown.integration.minecraft.MCTownInventory;
import ca.bradj.questown.integration.minecraft.MCTownItem;
import ca.bradj.questown.jobs.GathererJournal;
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
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.protocol.game.DebugPackets;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
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
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.entity.schedule.Schedule;
import net.minecraft.world.entity.schedule.ScheduleBuilder;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class VisitorMobEntity extends PathfinderMob {

    private static final String NBT_TOWN_X = "town_x";
    private static final String NBT_TOWN_Y = "town_y";
    private static final String NBT_TOWN_Z = "town_z";

    public static final String DEFAULT_SCHEDULE_ID = "visitor_default_schedule";
    public static final Schedule DEFAULT_SCHEDULE = new ScheduleBuilder(new Schedule())
            .changeActivityAt(10, Activity.IDLE)
            .changeActivityAt(12000, Activity.REST)
            .build();

    public static final Map<MemoryModuleType<GlobalPos>, BiPredicate<VisitorMobEntity, PoiType>> POI_MEMORIES = ImmutableMap.of(
            MemoryModuleType.HOME, (p_35493_, p_35494_) -> p_35494_ == PoiType.HOME
    );
    private static final ImmutableList<SensorType<? extends Sensor<? super Villager>>> SENSOR_TYPES = ImmutableList.of(
            SensorType.NEAREST_BED
    );

    private final VisitorMobJob job = new VisitorMobJob(level.isClientSide() ? null : (ServerLevel) level);

    private TownInterface town;
    boolean sitting = true;
    private BlockPos wanderTarget;

    public VisitorMobEntity(
            EntityType<? extends PathfinderMob> ownType,
            Level level,
            TownInterface town
    ) {
        super(ownType, level);
        // TODO: Store town UUID on NBT
        this.town = town;
        if (town != null) {
            initBrain();
        }
        job.initializeStatus(GathererStatuses.IDLE); // TODO: Read from NBT?
    }

    @Override
    public void tick() {
        super.tick();
        job.tick(level, blockPosition());
    }

    private void initBrain() {
        this.getBrain().setMemory(MemoryModuleType.LAST_SLEPT, Optional.empty());
        this.getBrain().setMemory(MemoryModuleType.LAST_WOKEN, Optional.empty());
        this.getBrain().setMemory(MemoryModuleType.WALK_TARGET, Optional.empty());
        this.getBrain().setMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE, Optional.empty());
        this.getBrain().setMemory(MemoryModuleType.PATH, Optional.empty());
    }

    @Override
    public boolean save(CompoundTag p_20224_) {
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
                this.initBrain();
            }
        }
    }

    public static AttributeSupplier setAttributes() {
        return PathfinderMob.createMobAttributes().build();
    }

    public static ImmutableList<Pair<Integer, ? extends Behavior<? super VisitorMobEntity>>> getCorePackage(
    ) {
        return ImmutableList.of(
                Pair.of(0, new Swim(0.8F)),
//                Pair.of(0, new InteractWithDoor()),
                Pair.of(0, new LookAtTargetSink(45, 90)),
                Pair.of(0, new WakeUp()),
                Pair.of(1, new MoveToTargetSink()),
                Pair.of(10, new AcquirePoi(PoiType.HOME, MemoryModuleType.HOME, false, Optional.of((byte) 14)))
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
                Pair.of(3, new ValidateNearbyPoi(PoiType.HOME, MemoryModuleType.HOME)),
                Pair.of(3, new SleepInBed()),
                Pair.of(
                        5,
                        new RunOne<>(
                                ImmutableMap.of(MemoryModuleType.HOME, MemoryStatus.VALUE_ABSENT),
                                ImmutableList.of(
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
        b.add(Pair.of(2, new DoNothing(30, 60)));
        b.add(Pair.of(4, new Admire(200)));
        b.add(Pair.of(3, new TownWalk(0.25f)));
        b.add(Pair.of(10, new TownWalk(0.40f)));
        b.add(Pair.of(99, new UpdateActivityFromSchedule()));
        return b.build();
    }

    @Override
    protected PathNavigation createNavigation(Level p_21480_) {
        GroundPathNavigation gpn = new GroundPathNavigation(this, p_21480_);
        gpn.setCanOpenDoors(true);
        gpn.setCanPassDoors(true);
        return gpn;
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
        BlockPos target = job.getTarget(town);
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
                    recipe = new UIQuest(SpecialQuests.SPECIAL_QUESTS.get(id), Quest.QuestStatus.ACTIVE);
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

        return InteractionResult.sidedSuccess(isClientSide);
    }
}

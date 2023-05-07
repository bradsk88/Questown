package ca.bradj.questown.mobs.visitor;

import ca.bradj.questown.Questown;
import ca.bradj.questown.town.interfaces.TownInterface;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Dynamic;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.protocol.game.DebugPackets;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
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
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.entity.schedule.Schedule;
import net.minecraft.world.entity.schedule.ScheduleBuilder;
import net.minecraft.world.level.Level;

import java.util.Map;
import java.util.Optional;
import java.util.function.BiPredicate;

public class VisitorMobEntity extends PathfinderMob {

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

    private final TownInterface town;
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
            this.getBrain().setMemory(MemoryModuleType.LAST_SLEPT, Optional.empty());
            this.getBrain().setMemory(MemoryModuleType.LAST_WOKEN, Optional.empty());
            this.getBrain().setMemory(MemoryModuleType.WALK_TARGET, Optional.empty());
            this.getBrain().setMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE, Optional.empty());
            this.getBrain().setMemory(MemoryModuleType.PATH, Optional.empty());
        }
    }

    public static AttributeSupplier setAttributes() {
        return PathfinderMob.createMobAttributes().build();
    }

    public static ImmutableList<Pair<Integer, ? extends Behavior<? super VisitorMobEntity>>> getCorePackage(
    ) {
        return ImmutableList.of(
                Pair.of(0, new Swim(0.8F)),
                Pair.of(0, new InteractWithDoor()),
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
    ) {
        return ImmutableList.of(
                Pair.of(
                        2,
                        new RunOne<>(ImmutableList.of(
                                Pair.of(new DoNothing(30, 60), 1)
                        ))
                ),
                Pair.of(99, new UpdateActivityFromSchedule())
        );
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
                MemoryModuleType.INTERACTION_TARGET
        ), SENSOR_TYPES);
    }

    @Override
    protected Brain<?> makeBrain(Dynamic<?> p_21069_) {
        Brain<VisitorMobEntity> brain = (Brain<VisitorMobEntity>) super.makeBrain(p_21069_);
        brain.setSchedule(VisitorMobEntity.DEFAULT_SCHEDULE);
        brain.addActivity(Activity.IDLE, getIdlePackage());
        brain.addActivity(Activity.REST, getRestPackage());
        brain.addActivity(Activity.CORE, getCorePackage());
        brain.setCoreActivities(ImmutableSet.of(Activity.CORE));
        brain.updateActivityFromSchedule(this.level.getDayTime(), this.level.getGameTime());
        return brain;
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

    public void newWanderTarget() {
        if (town == null) {
            this.kill();
            return;
        }
        this.setWanderTarget(town.getWanderTarget());
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
}

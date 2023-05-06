package ca.bradj.questown.mobs.visitor;

import ca.bradj.questown.town.interfaces.TownInterface;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Position;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.OpenDoorGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.BlockPathTypes;

public class VisitorMobEntity extends PathfinderMob {

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
    }

    public static AttributeSupplier setAttributes() {
        return PathfinderMob.createMobAttributes().build();
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
        this.goalSelector.addGoal(1, new SlowWake(this));
        this.goalSelector.addGoal(2, new OpenDoorGoal(this, true));
        this.goalSelector.addGoal(3, new TownWalk(this, 2.0D, 0.25D));
        this.goalSelector.addGoal(4, new TownWalk(this, 2.0D, 0.50D));
        this.goalSelector.addGoal(5, new RandomLookAroundGoal(this));
        this.goalSelector.addGoal(11, new LookAtPlayerGoal(this, Player.class, 8.0F));
    }

    public boolean isSitting() {
        return this.sitting;
    }

    public void setWanderTarget(BlockPos blockPos) {
        this.wanderTarget = blockPos;
    }

    public BlockPos getWanderTarget() {
        if (town == null) {
            this.kill();
            return new BlockPos(0, 0, 0);
        }
        return this.wanderTarget;
    }

    public void newWanderTarget() {
        if (town == null) {
            this.kill();
            return;
        }
        this.wanderTarget = town.getWanderTarget();
    }
}

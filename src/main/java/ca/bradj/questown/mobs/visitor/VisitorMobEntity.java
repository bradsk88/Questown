package ca.bradj.questown.mobs.visitor;

import ca.bradj.questown.town.TownFlagBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

public class VisitorMobEntity extends PathfinderMob {

    private final TownFlagBlockEntity townFlag;

    public VisitorMobEntity(
            EntityType<? extends PathfinderMob> ownType,
            Level level,
            TownFlagBlockEntity townFlag
    ) {
        super(ownType, level);
        this.townFlag = townFlag; // How can we avoid losing this reference on game reload?
    }

    public VisitorMobEntity(
            EntityType<? extends PathfinderMob> ownType,
            Level level
    ) {
        super(ownType, level);
        this.townFlag = null;
    }

    public static AttributeSupplier setAttributes() {
        return PathfinderMob.createMobAttributes().build();
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        this.goalSelector.addGoal(1, new RandomStrollGoal(this, 0.25));
        this.goalSelector.addGoal(10, new JoinTownGoal(this, () -> {
            if (townFlag != null) {
                return townFlag.getBlockPos();
            }
            // FIXME: Find the town flag via NBT coordinates?
            kill();
            return new BlockPos(0, 0, 0);
        }));
        this.goalSelector.addGoal(11, new LookAtPlayerGoal(this, Player.class, 8.0F));
    }
}

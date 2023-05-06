package ca.bradj.questown.mobs.visitor;

import ca.bradj.questown.Questown;
import ca.bradj.questown.town.TownFlagBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

public class VisitorMobEntity extends PathfinderMob {

    final TownFlagBlockEntity townFlag;
    private boolean goingHome = false;

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

        // TODO: Store flag entity UUID in NBT and use level.getEntityByUuid
        this.townFlag = null;
    }

    public static AttributeSupplier setAttributes() {
        return PathfinderMob.createMobAttributes().build();
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        this.goalSelector.addGoal(0, new RemoveSelfGoal(this));
        this.goalSelector.addGoal(1, new GoToFlagGoal(this, 0.5, 6));
        this.goalSelector.addGoal(2, new RandomStrollGoal(this, 0.25));
        this.goalSelector.addGoal(3, new VisitFlagGoal(this, 0.25, 2.5));
        this.goalSelector.addGoal(11, new LookAtPlayerGoal(this, Player.class, 8.0F));
    }

    static class GoToFlagGoal extends Goal {
        protected final VisitorMobEntity visitor;
        private final double speedModifier;
        private final double closeRadius;
        private boolean stuck;
        private int closeToHomeTryTicks;
        private static final int GIVE_UP_TICKS = 600;
        private int stuckTicks;

        GoToFlagGoal(
                VisitorMobEntity p_30253_,
                double speed,
                double closeRadius
        ) {
            this.visitor = p_30253_;
            this.speedModifier = speed;
            this.closeRadius = closeRadius;
        }

        public boolean canUse() {
            if (this.visitor.townFlag == null) {
                return false;
            }
            return this.closeToHomeTryTicks <= this.adjustedTickDelay(600) ||
                    this.stuckTicks <= this.adjustedTickDelay(600);
        }

        public void start() {
            Questown.LOGGER.debug(
                    "Visitor {} going to flag (close radius = {})",
                    this.visitor.getUUID(),
                    this.closeRadius
            );
            this.visitor.setGoingHome(true);
            this.stuck = false;
            this.closeToHomeTryTicks = 0;
            this.stuckTicks = 0;
        }

        public void stop() {
            this.visitor.setGoingHome(false);
        }

        public boolean canContinueToUse() {
            if (this.visitor.townFlag == null) {
                return false;
            }
            BlockPos home = this.visitor.getHomePos();
            Vec3 pos = this.visitor.position();
            boolean atFlag = home.closerToCenterThan(pos, 2.0D);
            boolean can = !atFlag &&
                    this.closeToHomeTryTicks <= this.adjustedTickDelay(600) &&
                    this.stuckTicks <= this.adjustedTickDelay(600);
            return can;
        }

        public void tick() {
            if (this.visitor.townFlag == null) {
                return;
            }

            BlockPos blockpos = this.visitor.getHomePos();

            if (stuck) {
                stuckTicks++;
                blockpos = blockpos.relative(Direction.getRandom(this.visitor.getRandom()), 10);
            }

            boolean closeToHome = blockpos.closerToCenterThan(this.visitor.position(), closeRadius);
            if (closeToHome) {
                ++this.closeToHomeTryTicks;
            }

            if (this.visitor.getNavigation().isDone()) {
                Vec3 vec3 = Vec3.atBottomCenterOf(blockpos);
                Vec3 vec31 = DefaultRandomPos.getPosTowards(
                        this.visitor,
                        16,
                        3,
                        vec3,
                        (float) Math.PI / 10F
                );
                if (vec31 == null) {
                    vec31 = DefaultRandomPos.getPosTowards(this.visitor, 8, 7, vec3, (double) ((float) Math.PI / 2F));
                }

                if (vec31 != null && !closeToHome && !this.visitor.level.getBlockState(new BlockPos(vec31))
                        .is(Blocks.WATER)) {
                    vec31 = DefaultRandomPos.getPosTowards(this.visitor, 16, 5, vec3, (double) ((float) Math.PI / 2F));
                }

                if (vec31 == null) {
                    this.stuck = true;
                    return;
                }

                this.visitor.getNavigation().moveTo(vec31.x, vec31.y, vec31.z, this.speedModifier);
            }

        }
    }

    private void setGoingHome(boolean b) {
        this.goingHome = b;
    }

    private BlockPos getHomePos() {
        return this.townFlag.getBlockPos();
//        return new Vec3i(bp.getX(), bp.getY(), bp.getZ());
    }

    static class VisitFlagGoal extends GoToFlagGoal {

        VisitFlagGoal(
                VisitorMobEntity p_30253_,
                double speed,
                double closeRadius
        ) {
            super(p_30253_, speed, closeRadius);
        }

        @Override
        public boolean canUse() {
            if (visitor.goingHome) {
                return false;
            }
            return true;
        }
    }
}

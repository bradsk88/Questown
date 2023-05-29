package ca.bradj.questown.town;

import ca.bradj.questown.logic.TownCycle;
import ca.bradj.questown.town.special.SpecialQuests;
import ca.bradj.roomrecipes.core.Room;
import ca.bradj.roomrecipes.core.space.Position;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

import java.util.Collection;
import java.util.Optional;

public class TownPois {

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public Vec3 getVisitorJoinPos(ServerLevel level, BlockPos fallback) {
        if (this.visitorSpot == null) {
            return new Vec3(fallback.getX(), fallback.getY(), fallback.getZ());
        }
        BlockPos vs = this.visitorSpot.relative(Direction.Plane.HORIZONTAL.getRandomDirection(level.getRandom()), 2);
        // TODO: No while loops. Do this logic in the tick
        while (!level.isUnobstructed(level.getBlockState(vs.below()), vs, CollisionContext.empty())) {
            vs = this.visitorSpot.relative(Direction.Plane.HORIZONTAL.getRandomDirection(level.getRandom()), 2);
        }
        return new Vec3(vs.getX(), vs.getY(), vs.getZ());
    }

    public Position getWanderTarget(ServerLevel level, Collection<Room> all) {
        // TODO: Put these on a stack each tick. Don't iterate every room on every tick.
        for (Room r : all) {
            if (level.getRandom().nextInt(all.size()) == 0) {
                Position ac = r.getSpace().getCornerA();
                Position bc = r.getSpace().getCornerB();
                if (level.getRandom().nextBoolean()) {
                    return new Position((ac.x + bc.x) / 2, (ac.z + bc.z) / 2);
                }
                if (level.getRandom().nextBoolean()) {
                    return ac.offset(1, 1);
                }
                if (level.getRandom().nextBoolean()) {
                    return bc.offset(-1, -1);
                }
                return r.getDoorPos();
            }
        }
        return null;
    }

    public interface Listener {
        void campfireFound();
    }

    private Listener listener;

    private BlockPos visitorSpot = null;

    public void tick(ServerLevel level, BlockPos flagPos) {
        // TODO: Consider adding non-room town "features" as quests
        // TODO: Don't check this so often - maybe add fireside seating that can be paired to flag block
        Optional<BlockPos> fire = TownCycle.findCampfire(flagPos, level);
        if (visitorSpot == null) {
            fire.ifPresent((bp) -> listener.campfireFound());
        }
        visitorSpot = fire.orElse(null);
    }

    // TODO: Add town gate, etc (in future)
}

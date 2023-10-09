package ca.bradj.questown.town;

import ca.bradj.questown.Questown;
import ca.bradj.questown.core.init.BlocksInit;
import ca.bradj.questown.logic.TownCycle;
import ca.bradj.roomrecipes.core.Room;
import ca.bradj.roomrecipes.core.space.Position;
import com.google.common.collect.ImmutableList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class TownPois {

    private List<BlockPos> welcomeMats = new ArrayList<>();

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

    public interface Filter<R extends Room> {
        boolean include(Position positionIn, R containingRoom);
    }

    public interface PositionFactory<P, R extends Room> {
        P make(Position p, R containingRoom);
    }

    public <R extends Room, P> P getWanderTarget(
            ServerLevel level,
            Collection<R> all,
            Filter<R> filter,
            PositionFactory<P, R> pFact
    ) {
        // TODO: Put these on a stack each tick. Don't iterate every room on every tick.
        R r = ImmutableList.copyOf(all).get(level.getRandom().nextInt(all.size()));
        Position ac = r.getSpace().getCornerA();
        Position bc = r.getSpace().getCornerB();

        ImmutableList.Builder<P> b = ImmutableList.builder();

        Position center = new Position((ac.x + bc.x) / 2, (ac.z + bc.z) / 2);
        if (filter.include(center, r)) {
            b.add(pFact.make(center, r));
        }
        Position aCorner = ac.offset(1, 1); // TODO: These offsets still needed?
        if (filter.include(aCorner, r)) {
            b.add(pFact.make(aCorner, r));
        }
        Position bCorner = bc.offset(-1, -1);
        if (filter.include(bCorner, r)) {
            b.add(pFact.make(bCorner, r));
        }
        Position doorPos = r.getDoorPos();
        if (filter.include(doorPos, r)) {
            b.add(pFact.make(doorPos, r));
        }

        ImmutableList<P> positions = b.build();
        if (positions.isEmpty()) {
            return null;
        }
        return positions.get(level.getRandom().nextInt(positions.size()));
    }

    public @Nullable BlockPos getWelcomeMatPos(@NotNull ServerLevel level) {
        while (welcomeMats.size() > 0) {
            ImmutableList<BlockPos> copy = ImmutableList.copyOf(welcomeMats);
            int index = level.random.nextInt(copy.size());
            BlockPos mat = copy.get(index);
            if (!level.getBlockState(mat).is(BlocksInit.WELCOME_MAT_BLOCK.get())) {
                welcomeMats.remove(index);
                continue;
            }
            return mat;
        }
        return null;
    }

    public void registerWelcomeMat(BlockPos welcomeMatBlock) {
        this.welcomeMats.add(welcomeMatBlock);
        Questown.LOGGER.debug("Welcome mat was registered with town flag at {}", welcomeMatBlock);
    }

    public ImmutableList<BlockPos> getWelcomeMats() {
        return ImmutableList.copyOf(this.welcomeMats);
    }

    public interface Listener {
        void campfireFound(BlockPos pos);
        void townGateFound(BlockPos pos);
    }

    private Listener listener;

    private BlockPos visitorSpot = null;

    public void tick(ServerLevel level, BlockPos flagPos) {
        // TODO: Consider adding non-room town "features" as quests
        // TODO: Don't check this so often - maybe add fireside seating that can be paired to flag block
        Optional<BlockPos> fire = TownCycle.findCampfire(flagPos, level);
        fire.ifPresent((bp) -> listener.campfireFound(bp));

        BlockPos welcomePos = getWelcomeMatPos(level);
        if (welcomePos == null) {
            visitorSpot = fire.orElse(visitorSpot);
            return;
        }

        BlockPos gate = TownCycle.findTownGate(welcomePos, level,
                position -> WallDetection.IsWall(level, position, welcomePos.getY())
        );
        if (gate != null) {
            listener.townGateFound(gate);
            visitorSpot = gate;
        }
    }

    // TODO: Add town gate, etc (in future)
}

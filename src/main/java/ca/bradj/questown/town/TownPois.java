package ca.bradj.questown.town;

import ca.bradj.questown.QT;
import ca.bradj.questown.blocks.TownFlagSubBlocks;
import ca.bradj.questown.core.Config;
import ca.bradj.questown.core.init.BlocksInit;
import ca.bradj.questown.logic.TownCycle;
import ca.bradj.roomrecipes.core.Room;
import ca.bradj.roomrecipes.core.space.Position;
import ca.bradj.roomrecipes.logic.InclusiveSpaces;
import com.google.common.collect.ImmutableList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

// TODO: Clarify the purpose of "POIs"
public class TownPois {

    private final TownFlagSubBlocks subBlocks;
    private List<BlockPos> welcomeMats = new ArrayList<>();

    public TownPois(TownFlagSubBlocks subBlocks) {
        this.subBlocks = subBlocks;
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public Vec3 getVisitorJoinPos(
            ServerLevel level,
            BlockPos fallback
    ) {
        if (this.visitorSpot == null) {
            return new Vec3(fallback.getX(), fallback.getY(), fallback.getZ());
        }
        // Never put an arriving villager next to the campfire, where it would catch fire.
        var campfire = TownCycle.findCampfire(fallback, level, bs -> true);
        // TODO: No while loops. Do this logic in the tick
        for (int i = 0; i < Config.BASE_MAX_LOOP.get(); i++) {
            BlockPos vs = this.visitorSpot.relative(Direction.Plane.HORIZONTAL.getRandomDirection(level.getRandom()), 2);
            if (level.isUnobstructed(level.getBlockState(vs.below()), vs, CollisionContext.empty())
                    && !isWithinBurnRadius(vs, campfire)) {
                return new Vec3(vs.getX(), vs.getY(), vs.getZ());
            }
        }
        // Every try was blocked: use the first one.
        BlockPos vs = this.visitorSpot.relative(Direction.Plane.HORIZONTAL.getRandomDirection(level.getRandom()), 2);
        return new Vec3(vs.getX(), vs.getY(), vs.getZ());
    }

    /** Whether {@code pos} is on or next to a campfire. */
    private static boolean isWithinBurnRadius(BlockPos pos, Optional<BlockPos> campfire) {
        return campfire.map(cf -> Math.max(Math.abs(cf.getX() - pos.getX()), Math.abs(cf.getZ() - pos.getZ())) <= 1).orElse(false);
    }

    public interface Filter<R extends Room> {
        boolean include(
                Position positionIn,
                R containingRoom
        );
    }

    public interface PositionFactory<P, R extends Room> {
        P make(
                Position p,
                R containingRoom
        );
    }

    public <R extends Room, P> @Nullable P getWanderTarget(
            ServerLevel level,
            Collection<R> all,
            Filter<R> filter,
            PositionFactory<P, R> pFact
    ) {
        return getWanderTarget(level, all, filter, pFact, null);
    }

    /** As above, reporting why a search came back empty. */
    public <R extends Room, P> @Nullable P getWanderTarget(
            ServerLevel level,
            Collection<R> all,
            Filter<R> filter,
            PositionFactory<P, R> pFact,
            @Nullable WanderDiagnostics diagnostics
    ) {
        if (all.isEmpty()) {
            if (diagnostics != null) {
                diagnostics.noRooms();
            }
            return null;
        }
        R r = ImmutableList.copyOf(all).get(level.getRandom().nextInt(all.size()));

        Collection<Position> allEnclosed = r.getSpaces()
                                            .stream()
                                            .map(v -> InclusiveSpaces.getPositions(v, InclusiveSpaces.PositionType.INTERIOR_ONLY))
                                            .flatMap(Collection::stream)
                                            .toList();

        ImmutableList.Builder<P> b = ImmutableList.builder();
        for (Position p : allEnclosed) {
            if (filter.include(p, r)) {
                b.add(pFact.make(p, r));
            }
        }

        ImmutableList<P> positions = b.build();
        if (positions.isEmpty()) {
            if (diagnostics != null) {
                diagnostics.noCandidateInRoom(all.size(), allEnclosed.size());
            }
            return null;
        }
        return positions.get(level.getRandom().nextInt(positions.size()));
    }

    /** Why a wander-target search found nothing. */
    public interface WanderDiagnostics {
        /** The town has no rooms. */
        void noRooms();

        /** There are rooms, but the chosen one had no suitable position. */
        void noCandidateInRoom(int roomCount, int enclosedCount);
    }

    public @Nullable BlockPos getWelcomeMatPos(@NotNull ServerLevel level) {
        for (int i = 0; i < Config.BASE_MAX_LOOP.get(); i++) {
            if (welcomeMats.isEmpty()) {
                break;
            }
            ImmutableList<BlockPos> copy = ImmutableList.copyOf(welcomeMats);
            int index = level.random.nextInt(copy.size());
            BlockPos mat = copy.get(index);
            if (!level.getBlockState(mat).is(BlocksInit.WELCOME_MAT_BLOCK.get())) {
                // Skip it, don't forget it: welcome mats are only re-added at placement or load.
                continue;
            }
            return mat;
        }
        return null;
    }

    public void registerWelcomeMat(BlockPos welcomeMatBlock) {
        this.welcomeMats.add(welcomeMatBlock);
        this.subBlocks.register(welcomeMatBlock);
        QT.FLAG_LOGGER.debug("Welcome mat was registered with town flag at {}", welcomeMatBlock);
    }

    public ImmutableList<BlockPos> getWelcomeMats() {
        return ImmutableList.copyOf(this.welcomeMats);
    }

    private static boolean isCampfireLit(net.minecraft.world.level.block.state.BlockState bs) {
        return bs.hasProperty(CampfireBlock.LIT) && bs.getValue(CampfireBlock.LIT);
    }

    public interface Listener {
        void campfireFound(BlockPos pos);

        void townGateFound(BlockPos pos);
    }

    private Listener listener;

    private BlockPos visitorSpot = null;

    public void tick(
            ServerLevel level,
            BlockPos flagPos,
            boolean campfireQuestOpen
    ) {
        // TODO: Consider adding non-room town "features" as quests
        // TODO: Don't check this so often - maybe add fireside seating that can be paired to flag block
        Optional<BlockPos> fire = Optional.empty();
        if (campfireQuestOpen) {
            // Only a lit campfire completes the Campfire quest; the player lights the scaffolding's campfire with the wand.
            fire = TownCycle.findCampfire(flagPos, level, TownPois::isCampfireLit);
            fire.ifPresent((bp) -> listener.campfireFound(bp));
        }
        BlockPos welcomePos = getWelcomeMatPos(level);
        if (welcomePos == null) {
            visitorSpot = fire.orElse(visitorSpot);
            return;
        }

        BlockPos gate = TownCycle.findTownGate(
                welcomePos, level,
                position -> WallDetection.IsWall(level, position, welcomePos.getY())
        );
        if (gate != null) {
            listener.townGateFound(gate);
            visitorSpot = gate;
        } else {
            // The mat is placed but its gate doesn't resolve yet: arrivals come to the mat.
            visitorSpot = welcomePos;
        }
    }

    // TODO: Add town gate, etc (in future)
}

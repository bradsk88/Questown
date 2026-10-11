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
        // Never hand out a join position in a campfire's burn radius: a townie
        // spawning adjacent to the (player's, lit) campfire catches fire and
        // burns to death seconds after arriving. The campfire sits a few blocks
        // from the flag while join positions ring the gate, so the two overlap
        // (village/growth: first arrival died at the campfire, 2026-10-21).
        var campfire = TownCycle.findCampfire(fallback, level, bs -> true);
        // TODO: No while loops. Do this logic in the tick
        for (int i = 0; i < Config.BASE_MAX_LOOP.get(); i++) {
            BlockPos vs = this.visitorSpot.relative(Direction.Plane.HORIZONTAL.getRandomDirection(level.getRandom()), 2);
            if (level.isUnobstructed(level.getBlockState(vs.below()), vs, CollisionContext.empty())
                    && !isWithinBurnRadius(vs, campfire)) {
                return new Vec3(vs.getX(), vs.getY(), vs.getZ());
            }
        }
        // All attempts blocked (shouldn't happen): fall back to the first roll,
        // matching the pre-campfire behaviour.
        BlockPos vs = this.visitorSpot.relative(Direction.Plane.HORIZONTAL.getRandomDirection(level.getRandom()), 2);
        return new Vec3(vs.getX(), vs.getY(), vs.getZ());
    }

    /**
     * Chebyshev distance check: true when {@code pos} sits on or within one ring
     * of a campfire (a campfire ignites entities on or touching its block).
     */
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

    /**
     * As {@link #getWanderTarget(ServerLevel, Collection, Filter, PositionFactory)},
     * but reports *why* a search came back empty to {@code diagnostics} (if given).
     * Keeping the reason out of this method's return type lets the caller log a
     * specific, actionable message once instead of a vague per-tick warning.
     */
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

    /** Why a wander-target search returned null. One method fires per search. */
    public interface WanderDiagnostics {
        /** The town has no rooms to wander into at all (day and night both checked). */
        void noRooms();

        /** Rooms exist, but the chosen one contributed no position passing the filter. */
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
                // Skip, don't drop. welcomeMats is only (re)populated at placement
                // (registerWelcomeMat) or flag load (NBT restore), so a single
                // transient non-mat read used to remove the mat for good - and with it
                // its TOWN_GATE job sites, soft-locking leavers in COLLECTING_SUPPLIES
                // with no in-run recovery (2026-10-07 sweep: the list flapped 0<->1,
                // jobRooms was 0 in stalls / 1 in passes). Leaving it in degrades a
                // truly-gone mat to NO_JOBSITE (recoverable), not a permanent stall.
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
            int villagerCount
    ) {
        // TODO: Consider adding non-room town "features" as quests
        // TODO: Don't check this so often - maybe add fireside seating that can be paired to flag block
        Optional<BlockPos> fire = Optional.empty();
        if (villagerCount <= 0) {
            // Quest completion fires only when a *lit* campfire exists nearby and
            // no villager has arrived yet (day one) — an unlit worldgen campfire
            // (e.g. inside the empty_town structure) should not auto-complete the
            // quest on player arrival. The chicken-arc UX expects the player to use
            // the wand on the campfire first, lighting it, which is what advances
            // both the chicken's WAITING_FOR_WAND_ON_CAMPFIRE beat and this special
            // quest. The old "visitorSpot == null" co-condition is gone: with the
            // mat-gated arrival (.scratch/mat-gated-arrival) the mat sets
            // visitorSpot before the first arrival, and a player who places the mat
            // before lighting the campfire would otherwise lose the campfire quest
            // (markQuestAsComplete is idempotent, so the no-villager gate is the
            // one-shot guard).
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
            // Mat is registered but the flanked-gate structure does not resolve
            // yet: spawn at the mat itself rather than leaving visitorSpot null
            // (flag fallback). The mat IS the gate as far as arrival is concerned
            // (.scratch/mat-gated-arrival, decision 1 + "Arrival position edge").
            visitorSpot = welcomePos;
        }
    }

    // TODO: Add town gate, etc (in future)
}

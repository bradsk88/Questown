package ca.bradj.questown.jobs;

import ca.bradj.questown.blocks.JobBlock;
import ca.bradj.questown.town.AbstractWorkStatusStore;
import ca.bradj.roomrecipes.adapter.RoomWithBlocks;
import ca.bradj.roomrecipes.core.Room;
import ca.bradj.roomrecipes.logic.InclusiveSpaces;
import ca.bradj.roomrecipes.rooms.XWall;
import ca.bradj.roomrecipes.rooms.ZWall;
import com.google.common.collect.ImmutableList;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;

public class JobSites {

    public static <POS, MATCH extends RoomWithBlocks<ROOM, POS, ?>, ROOM extends Room> POS find(
            Supplier<? extends Collection<MATCH>> matches,
            Function<MATCH, Collection<? extends Map.Entry<POS, ?>>> containedBlocks,
            Function<MATCH, ROOM> room,
            Function<POS, AbstractWorkStatusStore. @Nullable State> getState,
            Map<Integer, Boolean> statusItems,
            int maxState,
            ImmutableList<String> specialGlobalRules,
            PosKit<POS> poskit
    ) {
        List<MATCH> rooms = new ArrayList<>(matches.get());

        // TODO: Sort by distance and choose the closest (maybe also coordinate
        //  with other workers who need the same type of job site)
        // For now, we use randomization
        Collections.shuffle(rooms);

        for (MATCH match : rooms) {
            for (Map.Entry<POS, ?> blocks : containedBlocks.apply(match)) {
                POS blockPos = blocks.getKey();
                @Nullable Integer blockState = JobBlock.getState(getState, blockPos);
                if (blockState == null) {
                    continue;
                }
                if (maxState == blockState) {
                    return blockPos;
                }
                boolean shouldGo = statusItems.getOrDefault(blockState, false);
                if (shouldGo) {
                    return findInteractionSpot(
                            blockPos,
                            room.apply(match),
                            specialGlobalRules,
                            poskit
                    );
                }
            }
        }

        return null;
    }

    public static <P> P findInteractionSpot(
            P bp,
            Room jobSite,
            ImmutableList<String> specialGlobalRules,
            PosKit<P> poskit
    ) {
        @Nullable P spot;

        if (specialGlobalRules.contains(SpecialRules.PREFER_INTERACTION_BELOW)) {
            spot = doFindInteractionSpot(poskit.below(bp), jobSite, poskit);
            if (spot != null) {
                return spot;
            }
        }

        spot = doFindInteractionSpot(bp, jobSite, poskit);
        if (spot != null) {
            return spot;
        }

        return poskit.randomAdjacent(bp);
    }

    @Nullable
    private static <P> P doFindInteractionSpot(
            P bp,
            Room jobSite,
            PosKit<P> poskit
    ) {
        P d = getDoorSideAdjacentPosition(jobSite, poskit, bp);
        if (poskit.isEmpty(d)) {
            return d;
        }
        for (P dd : poskit.allDirs(bp)) {
            if (poskit.isEmpty(dd)) {
                return dd;
            }
        }
        if (InclusiveSpaces.calculateArea(jobSite.getSpaces()) == 9) {
            // 1x1 room (plus walls)
            return poskit.fromPosition(jobSite.getDoorPos(), bp);
        }
        return null;
    }

    public static <P> P getDoorSideAdjacentPosition(
            Room jobSite,
            Shifter<P> shifter,
            P ref
    ) {
        Optional<XWall> backXWall = jobSite.getBackXWall();
        if (backXWall.isPresent() && backXWall.get()
                                              .getZ() > jobSite.doorPos.z) {
            return shifter.north(ref);
        }
        if (backXWall.isPresent()) {
            return shifter.south(ref);
        }


        Optional<ZWall> backZWall = jobSite.getBackZWall();
        if (backZWall.isPresent() && backZWall.get()
                                              .getX() > jobSite.doorPos.x) {
            return shifter.west(ref);
        }
        if (backZWall.isPresent()) {
            return shifter.east(ref);
        }
        return shifter.north(ref);
    }
}

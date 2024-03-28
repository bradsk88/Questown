package ca.bradj.questown.jobs;

import ca.bradj.roomrecipes.core.space.Position;
import com.google.common.collect.ImmutableList;

import java.util.Collection;

public class TestPosKit implements PosKit<Position> {
    @Override
    public Position below(Position bp) {
        return bp;
    }

    @Override
    public Position randomAdjacent(Position bp) {
        return bp.offset(1, 0);
    }

    @Override
    public boolean isEmpty(Position d) {
        return false;
    }

    @Override
    public Collection<Position> allDirs(Position bp) {
        return ImmutableList.of(
                bp.offset(1, 0),
                bp.offset(-1, 0),
                bp.offset(0, 1),
                bp.offset(0, -1)
        );
    }

    @Override
    public Position fromPosition(
            Position doorPos,
            Position ref
    ) {
        return doorPos;
    }

    @Override
    public Position north(Position ref) {
        return ref.offset(0, -1);
    }

    @Override
    public Position south(Position ref) {
        return ref.offset(0, 1);
    }

    @Override
    public Position east(Position ref) {
        return ref.offset(1, 0);
    }

    @Override
    public Position west(Position ref) {
        return ref.offset(-1, 0);
    }
}

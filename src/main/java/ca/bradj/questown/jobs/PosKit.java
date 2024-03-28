package ca.bradj.questown.jobs;

import ca.bradj.roomrecipes.core.space.Position;

import java.util.Collection;

public interface PosKit<POS> {
    POS below(POS bp);

    POS randomAdjacent(POS bp);

    boolean isEmpty(POS d);

    Collection<POS> allDirs(POS bp);

    POS fromPosition(
            Position doorPos,
            POS ref
    );

    POS north(POS ref);

    POS south(POS ref);

    POS east(POS ref);

    POS west(POS ref);
}

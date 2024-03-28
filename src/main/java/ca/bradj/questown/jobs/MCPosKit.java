package ca.bradj.questown.jobs;

import ca.bradj.questown.mc.Compat;
import ca.bradj.roomrecipes.adapter.Positions;
import ca.bradj.roomrecipes.core.space.Position;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.Collection;
import java.util.Random;
import java.util.function.Predicate;

public class MCPosKit implements PosKit<BlockPos> {

    private final Random random;
    private final Predicate<BlockPos> isEmpty;

    public MCPosKit(
            Random random,
            Predicate<BlockPos> isEmpty
    ) {
        this.random = random;
        this.isEmpty = isEmpty;
    }

    @Override
    public BlockPos below(BlockPos bp) {
        return bp.below();
    }

    public BlockPos randomAdjacent(BlockPos blockPos) {
        return blockPos.relative(Compat.getRandomHorizontal(random));
    }

    @Override
    public boolean isEmpty(BlockPos d) {
        return isEmpty.test(d);
    }

    @Override
    public Collection<BlockPos> allDirs(BlockPos bp) {
        return Direction.Plane.HORIZONTAL.stream()
                                         .map(
                                                 bp::relative
                                         )
                                         .toList();
    }

    @Override
    public BlockPos fromPosition(
            Position doorPos,
            BlockPos ref
    ) {
        return Positions.ToBlock(doorPos, ref.getY());
    }

    @Override
    public BlockPos north(BlockPos ref) {
        return ref.relative(Direction.NORTH);
    }

    @Override
    public BlockPos south(BlockPos ref) {
        return ref.relative(Direction.SOUTH);
    }

    @Override
    public BlockPos east(BlockPos ref) {
        return ref.relative(Direction.EAST);
    }

    @Override
    public BlockPos west(BlockPos ref) {
        return ref.relative(Direction.WEST);
    }
}

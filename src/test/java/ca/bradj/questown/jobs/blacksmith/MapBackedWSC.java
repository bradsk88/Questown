package ca.bradj.questown.jobs.blacksmith;

import ca.bradj.questown.town.AbstractWorkStatusStore.State;
import ca.bradj.questown.town.Claim;
import ca.bradj.questown.town.interfaces.ImmutableWorkStateContainer;
import ca.bradj.roomrecipes.core.space.Position;
import com.google.common.collect.ImmutableMap;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public class MapBackedWSC implements ImmutableWorkStateContainer<Position, Boolean> {

    Map<Position, State> map = new HashMap<>();

    @Override
    public @Nullable State getJobBlockState(Position bp) {
        return map.get(bp);
    }

    @Override
    public ImmutableMap<Position, State> getAll() {
        return ImmutableMap.copyOf(map);
    }

    @Override
    public Boolean setJobBlockState(
            Position bp,
            State bs
    ) {
        map.put(bp, bs);
        return true;
    }

    @Override
    public Boolean setJobBlockStateWithTimer(
            Position bp,
            State bs,
            int ticksToNextState
    ) {
        map.put(bp, bs);
        return true;
    }

    @Override
    public Boolean clearState(Position bp) {
        map.remove(bp);
        return true;
    }

    @Override
    public boolean claimSpot(
            Position bp,
            Claim claim
    ) {
        return true;
    }

    @Override
    public void clearClaim(Position position) {

    }

    @Override
    public boolean canClaim(
            Position position,
            Supplier<Claim> makeClaim
    ) {
        return true;
    }
}

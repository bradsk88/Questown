package ca.bradj.questown.town.interfaces;

import ca.bradj.questown.town.Claim;
import ca.bradj.questown.town.workstatus.State;
import com.google.common.collect.ImmutableMap;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

public interface ImmutableWorkStateContainer<POS, SELF> {

    @Nullable
    State getJobBlockState(POS bp);

    ImmutableMap<POS, State> getAll();

    SELF setJobBlockState(
            POS bp,
            State bs
    );

    SELF setJobBlockStateWithTimer(
            POS bp,
            State bs,
            int ticksToNextState
    );

    SELF clearState(POS bp);

    boolean claimSpot(POS bp, Claim claim);

    void clearClaim(POS position);

    boolean canClaim(POS position, Supplier<Claim> makeClaim);
}

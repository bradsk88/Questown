package ca.bradj.questown.town.interfaces;

import ca.bradj.questown.town.AbstractWorkStatusStore;
import ca.bradj.questown.town.Claim;
import com.google.common.collect.ImmutableMap;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

public interface ImmutableWorkStateContainer<POS, SELF> {

    @Nullable AbstractWorkStatusStore.State getJobBlockState(POS bp);

    ImmutableMap<POS, AbstractWorkStatusStore.State> getAll();

    SELF setJobBlockState(
            POS bp,
            AbstractWorkStatusStore.State bs
    );

    SELF setJobBlockStateWithTimer(
            POS bp,
            AbstractWorkStatusStore.State bs,
            int ticksToNextState
    );

    SELF clearState(POS bp);

    boolean claimSpot(POS bp, Claim claim);

    void clearClaim(POS position);

    boolean canClaim(POS position, Supplier<Claim> makeClaim);
}

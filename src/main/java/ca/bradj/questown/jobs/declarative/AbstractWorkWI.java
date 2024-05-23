package ca.bradj.questown.jobs.declarative;

import ca.bradj.questown.jobs.WorkSpot;
import ca.bradj.questown.town.AbstractWorkStatusStore;
import ca.bradj.questown.town.interfaces.ImmutableWorkStateContainer;
import com.google.common.collect.ImmutableMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.BiFunction;
import java.util.function.Function;

public abstract class AbstractWorkWI<POS, EXTRA, ITEM, TOWN> {

    private final ImmutableMap<Integer, Integer> workRequiredAtStates;
    private final BiFunction<EXTRA, Integer, @NotNull Integer> timeRequiredAtStates;
    private final ImmutableMap<Integer, Function<ITEM, Boolean>> toolsRequiredAtStates;

    public AbstractWorkWI(
            ImmutableMap<Integer, Integer> workRequiredAtStates,
            BiFunction<EXTRA, Integer, Integer> timeRequiredAtStates,
            ImmutableMap<Integer, @NotNull Function<ITEM, Boolean>> toolsRequiredAtStates
    ) {
        this.workRequiredAtStates = workRequiredAtStates;
        this.timeRequiredAtStates = timeRequiredAtStates;
        this.toolsRequiredAtStates = toolsRequiredAtStates;
    }

    public WithReason<TOWN> tryWork(
            EXTRA extra,
            WorkSpot<Integer, POS> ws,
            boolean canProgress
    ) {
        POS bp = ws.position();
        Integer curState = ws.action();
        Integer nextStepWork = workRequiredAtStates.getOrDefault(
                curState + 1, 0
        );
        if (nextStepWork == null) {
            nextStepWork = 0;
        }
        Integer nextStepTime = timeRequiredAtStates.apply(extra, curState + 1);
        WithReason<TOWN> updatedTown = applyWork(extra, bp, curState, nextStepWork, nextStepTime, canProgress);
        boolean didWork = updatedTown.value() != null;
        Function<ITEM, Boolean> itemBooleanFunction = toolsRequiredAtStates.get(curState);
        if (didWork && itemBooleanFunction != null) {
            return new WithReason<>(degradeTool(extra, updatedTown.value(), itemBooleanFunction), "Did work with tool (and " + updatedTown.reason() + ")");
        }
        return updatedTown;
    }

    protected abstract TOWN degradeTool(
            EXTRA extra,
            @Nullable TOWN tuwn,
            Function<ITEM, Boolean> itemBooleanFunction
    );

    private WithReason<@Nullable TOWN> applyWork(
            EXTRA extra,
            POS bp,
            int curState,
            int nextStepWork,
            int nextStepTime,
            boolean canProgress
    ) {
        ImmutableWorkStateContainer<POS, TOWN> sl = getWorkStatuses(extra);
        AbstractWorkStatusStore.State oldState = sl.getJobBlockState(bp);
        if (oldState == null) {
            oldState = initForState(curState);
        }
        AbstractWorkStatusStore.State bs = oldState.decrWork(getWorkSpeedOf10(extra));
        if (oldState.workLeft() > 0 && oldState.equals(bs)) {
            return new WithReason<>(null, "No work done due to mood?");
        }
        if (bs.workLeft() == 0 && canProgress) {
            bs = bs.incrProcessing().setWorkLeft(nextStepWork).setCount(0);
        }
        if (nextStepTime <= 0) {
            return new WithReason<>(sl.setJobBlockState(bp, bs), "Work was done (no timers)");
        } else {
            return new WithReason<>(sl.setJobBlockStateWithTimer(bp, bs, nextStepTime), "Work was done (and timer set)");
        }
    }

    protected abstract int getWorkSpeedOf10(EXTRA extra);

    private AbstractWorkStatusStore.State initForState(Integer curState) {
        Integer work = workRequiredAtStates.get(curState);
        if (work == null) {
            work = 0;
        }
        return AbstractWorkStatusStore.State.fresh().setWorkLeft(work).setProcessing(curState);
    }

    protected abstract ImmutableWorkStateContainer<POS, TOWN> getWorkStatuses(
            EXTRA extra
    );
}


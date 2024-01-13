package ca.bradj.questown.jobs.declarative;

import ca.bradj.questown.jobs.WorkSpot;
import ca.bradj.questown.town.AbstractWorkStatusStore;
import ca.bradj.questown.town.interfaces.ImmutableWorkStateContainer;
import com.google.common.collect.ImmutableMap;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;

public abstract class AbstractWorkWI<POS, EXTRA, ITEM, TOWN> {

    private final ImmutableMap<Integer, Integer> workRequiredAtStates;
    private final ImmutableMap<Integer, Integer> timeRequiredAtStates;
    private final ImmutableMap<Integer, Function<ITEM, Boolean>> toolsRequiredAtStates;

    public AbstractWorkWI(
            ImmutableMap<Integer, Integer> workRequiredAtStates,
            ImmutableMap<Integer, Integer> timeRequiredAtStates,
            ImmutableMap<Integer, Function<ITEM, Boolean>> toolsRequiredAtStates
    ) {
        this.workRequiredAtStates = workRequiredAtStates;
        this.timeRequiredAtStates = timeRequiredAtStates;
        this.toolsRequiredAtStates = toolsRequiredAtStates;
    }

    public TOWN tryWork(
            EXTRA extra,
            WorkSpot<Integer, POS> ws
    ) {
        POS bp = ws.position();
        Integer curState = ws.action();
        Integer nextStepWork = workRequiredAtStates.getOrDefault(
                curState + 1, 0
        );
        if (nextStepWork == null) {
            nextStepWork = 0;
        }
        Integer nextStepTime = timeRequiredAtStates.getOrDefault(
                curState + 1, 0
        );
        if (nextStepTime == null) {
            nextStepTime = 0;
        }
        nextStepTime = getAugmentedTime(extra, nextStepTime);
        TOWN updatedTown = applyWork(extra, bp, curState, nextStepWork, nextStepTime);
        boolean didWork = updatedTown != null;
        Function<ITEM, Boolean> itemBooleanFunction = toolsRequiredAtStates.get(curState);
        if (didWork && itemBooleanFunction != null) {
            return degradeTool(extra, updatedTown, itemBooleanFunction);
        }
        return updatedTown;
    }

    protected abstract Integer getAugmentedTime(EXTRA extra, Integer nextStepTime);

    protected abstract TOWN degradeTool(
            EXTRA extra,
            @Nullable TOWN tuwn,
            Function<ITEM, Boolean> itemBooleanFunction
    );

    private @Nullable TOWN applyWork(
            EXTRA extra,
            POS bp,
            Integer curState,
            Integer nextStepWork,
            Integer nextStepTime
    ) {
        ImmutableWorkStateContainer<POS, TOWN> sl = getWorkStatuses(extra);
        AbstractWorkStatusStore.State oldState = sl.getJobBlockState(bp);
        if (oldState == null) {
            oldState = initForState(curState);
        }
        AbstractWorkStatusStore.State bs = oldState.decrWork(getWorkSpeedOf10(extra));
        if (oldState.workLeft() > 0 && oldState.equals(bs)) {
            return null;
        }
        if (bs.workLeft() == 0) {
            bs = bs.incrProcessing().setWorkLeft(nextStepWork).setCount(0);
        }
        if (nextStepTime <= 0) {
            return sl.setJobBlockState(bp, bs);
        } else {
            return sl.setJobBlockStateWithTimer(bp, bs, nextStepTime);
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


package ca.bradj.questown.jobs.declarative;

import ca.bradj.questown.jobs.WorkSpot;
import ca.bradj.questown.town.AbstractWorkStatusStore;
import ca.bradj.questown.town.interfaces.WorkStateContainer;
import com.google.common.collect.ImmutableMap;

import java.util.function.Function;

public abstract class AbstractWorkWI<POS, EXTRA, ITEM> {

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

    public boolean tryWork(
            EXTRA extra,
            WorkSpot<Integer, POS> ws
    ) {
        POS bp = ws.position;
        Integer curState = ws.action;
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
        AbstractWorkStatusStore.State blockState = applyWork(extra, bp, curState, nextStepWork, nextStepTime);
        boolean didWork = blockState != null;
        Function<ITEM, Boolean> itemBooleanFunction = toolsRequiredAtStates.get(curState);
        if (didWork && itemBooleanFunction != null) {
            degradeTool(extra, itemBooleanFunction);
        }
        return didWork;
    }

    protected abstract void degradeTool(
            EXTRA extra,
            Function<ITEM, Boolean> itemBooleanFunction
    );

    private AbstractWorkStatusStore.State applyWork(
            EXTRA extra,
            POS bp,
            Integer curState,
            Integer nextStepWork,
            Integer nextStepTime
    ) {
        WorkStateContainer<POS> sl = getWorkStatuses(extra);
        AbstractWorkStatusStore.State oldState = sl.getJobBlockState(bp);
        if (oldState == null) {
            oldState = initForState(curState);
        }
        int workLeft = oldState.workLeft();
        AbstractWorkStatusStore.State bs;
        if (workLeft <= 0) {
            bs = initForState(curState + 1);
        } else {
            bs = oldState.decrWork();
        }
        if (oldState.equals(bs)) {
            return null;
        }
        if (bs.workLeft() == 0) {
            bs = bs.setWorkLeft(nextStepWork).setCount(0);
        }
        if (nextStepTime <= 0) {
            sl.setJobBlockState(bp, bs);
        } else {
            sl.setJobBlockStateWithTimer(bp, bs, nextStepTime);
        }
        return bs;
    }

    private AbstractWorkStatusStore.State initForState(Integer curState) {
        Integer work = workRequiredAtStates.get(curState);
        if (work == null) {
            work = 0;
        }
        return AbstractWorkStatusStore.State.fresh().setWorkLeft(work).setProcessing(curState);
    }

    protected abstract WorkStateContainer<POS> getWorkStatuses(EXTRA extra);
}

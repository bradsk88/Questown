package ca.bradj.questown.jobs.declarative;

import ca.bradj.questown.jobs.WorkSpot;
import ca.bradj.questown.jobs.WorkedSpot;
import ca.bradj.questown.logic.PredicateCollection;
import ca.bradj.questown.town.workstatus.State;
import org.jetbrains.annotations.Nullable;

import java.util.function.BiConsumer;

public abstract class AbstractWorkWI<POS, EXTRA, ITEM, TOWN> {

    private final BiConsumer<EXTRA, WorkSpot<Integer, POS>> preStateChangeCallback;
    private final WorkChecks<EXTRA, ITEM> checks;

    public AbstractWorkWI(
            WorkChecks<EXTRA, ITEM> checks,
            BiConsumer<EXTRA, WorkSpot<Integer, POS>> preStateChangeCallback
    ) {
        this.checks = checks;
        this.preStateChangeCallback = preStateChangeCallback;
    }

    public TOWN tryWork(
            EXTRA extra,
            WorkedSpot<POS> ws
    ) {
        POS bp = ws.workPosition();
        Integer curState = ws.stateAfterWork();
        int nextStepWork = checks.getWorkForStep(curState + 1, 0);
        Integer nextStepTime = checks.getTimeForStep(extra, curState + 1);
        if (nextStepTime == null) {
            nextStepTime = 0;
        }
        TOWN updatedTown = applyWork(extra, bp, curState, nextStepWork, nextStepTime);
        boolean didWork = updatedTown != null;
        PredicateCollection<ITEM, ?> itemBooleanFunction = checks.getToolsForStep(curState);
        if (didWork && itemBooleanFunction != null) {
            return degradeTool(extra, updatedTown, itemBooleanFunction);
        }
        return updatedTown;
    }

    protected abstract TOWN degradeTool(
            EXTRA extra,
            @Nullable TOWN town,
            PredicateCollection<ITEM, ?> itemBooleanFunction
    );

    private @Nullable TOWN applyWork(
            EXTRA extra,
            POS bp,
            int curState,
            int nextStepWork,
            int nextStepTime
    ) {
        State oldState = getJobBlockState(extra, bp);
        if (oldState == null) {
            oldState = initForState(curState);
        }
        State bs = oldState.decrWork(getWorkSpeedOf10(extra));
        if (oldState.hasWorkLeft() && oldState.equals(bs)) {
            return null;
        }


        if (!bs.hasWorkLeft()) {
            this.preStateChangeCallback.accept(extra, new WorkSpot<>(bp, curState, 0, bp));
            bs = bs.incrProcessing().setWorkLeft(nextStepWork).setCount(0);
        }
        if (nextStepTime <= 0) {
            return setJobBlockState(extra, bp, bs);
        } else {
            return setJobBlockStateWithTimer(extra, bp, bs, nextStepTime);
        }
    }

    protected abstract TOWN setJobBlockStateWithTimer(
            EXTRA extra,
            POS bp,
            State bs,
            int nextStepTime
    );

    protected abstract TOWN setJobBlockState(
            EXTRA extra,
            POS bp,
            State bs
    );

    protected abstract State getJobBlockState(
            EXTRA extra,
            POS bp
    );

    protected abstract int getWorkSpeedOf10(EXTRA extra);

    private State initForState(Integer curState) {
        int work = checks.getWorkForStep(curState, 0);
        return State.fresh().setWorkLeft(work).setProcessing(curState);
    }
}

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
            WorkedSpot<POS> ws,
            boolean degradeTool
    ) {
        POS bp = ws.workPosition();
        Integer curState = ws.state();
        int nextStepWork = checks.getWorkForStep(curState + 1, 0);
        Integer nextStepTime = checks.getTimeForStep(extra, curState + 1);
        if (nextStepTime == null) {
            nextStepTime = 0;
        }
        TOWN updatedTown = applyWork(extra, bp, curState, nextStepWork, nextStepTime);
        boolean didWork = updatedTown != null;
        PredicateCollection<ITEM, ?> itemBooleanFunction = checks.getToolsForStep(curState);
        if (degradeTool && didWork && itemBooleanFunction != null) {
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


        boolean actionCompleted = false;
        if (!bs.hasWorkLeft()) {
            this.preStateChangeCallback.accept(extra, new WorkSpot<>(bp, curState, 0, bp));
            bs = bs.incrProcessing().setWorkLeft(nextStepWork).setCount(0);
            actionCompleted = true;
        }
        TOWN result = nextStepTime <= 0
                ? setJobBlockState(extra, bp, bs)
                : setJobBlockStateWithTimer(extra, bp, bs, nextStepTime);
        if (actionCompleted) {
            return onWorkActionCompleted(extra, result);
        }
        return result;
    }

    /**
     * Fires once per <em>completed</em> work action (workLeft exhausted → the block advances
     * a processing state), on both the realtime and warp paths. This is the proficiency-leveling
     * seam (ADR-0010): timer jobs and no-proficiency jobs never reach it. Implementations may
     * return an updated {@code town}. Default: pass through.
     */
    protected TOWN onWorkActionCompleted(
            EXTRA extra,
            TOWN town
    ) {
        return town;
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

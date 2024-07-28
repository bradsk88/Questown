package ca.bradj.questown.jobs.declarative;

import ca.bradj.questown.jobs.WorkSpot;
import ca.bradj.questown.town.interfaces.ImmutableWorkStateContainer;
import ca.bradj.questown.town.workstatus.State;
import com.google.common.collect.ImmutableMap;
import org.apache.commons.lang3.function.TriFunction;
import org.apache.logging.log4j.util.TriConsumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

public abstract class AbstractWorkWI<POS, EXTRA, ITEM, TOWN> {

    private final ImmutableMap<Integer, Integer> workRequiredAtStates;
    private final BiFunction<EXTRA, Integer, @NotNull Integer> timeRequiredAtStates;
    private final ImmutableMap<Integer, Function<ITEM, Boolean>> toolsRequiredAtStates;
    private final BiConsumer<EXTRA, WorkSpot<Integer, POS>> preStateChangeCallback;

    public AbstractWorkWI(
            ImmutableMap<Integer, Integer> workRequiredAtStates,
            BiFunction<EXTRA, Integer, Integer> timeRequiredAtStates,
            ImmutableMap<Integer, @NotNull Function<ITEM, Boolean>> toolsRequiredAtStates,
            BiConsumer<EXTRA, WorkSpot<Integer, POS>> preStateChangeCallback
    ) {
        this.workRequiredAtStates = workRequiredAtStates;
        this.timeRequiredAtStates = timeRequiredAtStates;
        this.toolsRequiredAtStates = toolsRequiredAtStates;
        this.preStateChangeCallback = preStateChangeCallback;
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
        Integer nextStepTime = timeRequiredAtStates.apply(extra, curState + 1);
        TOWN updatedTown = applyWork(extra, bp, curState, nextStepWork, nextStepTime);
        boolean didWork = updatedTown != null;
        Function<ITEM, Boolean> itemBooleanFunction = toolsRequiredAtStates.get(curState);
        if (didWork && itemBooleanFunction != null) {
            return degradeTool(extra, updatedTown, itemBooleanFunction);
        }
        return updatedTown;
    }

    protected abstract TOWN degradeTool(
            EXTRA extra,
            @Nullable TOWN tuwn,
            Function<ITEM, Boolean> itemBooleanFunction
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
        if (oldState.workLeft() > 0 && oldState.equals(bs)) {
            return null;
        }


        if (bs.workLeftV2() == 0f) {
            this.preStateChangeCallback.accept(extra, new WorkSpot<>(bp, curState, 0, bp));
            bs = bs.incrProcessing().setWorkLeft(nextStepWork).setCount(0);
        }
        if (nextStepTime <= 0) {
            return setJobBlockState(extra, bp, bs);
        } else {
            return setJobBlockStateWithTimer(extra, bp, bs, nextStepTime);
        }
    }

    protected abstract TOWN setJobBlockStateWithTimer(EXTRA extra, POS bp, State bs, int nextStepTime);

    protected abstract TOWN setJobBlockState(EXTRA extra, POS bp, State bs);

    protected abstract State getJobBlockState(EXTRA extra, POS bp);

    protected abstract int getWorkSpeedOf10(EXTRA extra);

    private State initForState(Integer curState) {
        Integer work = workRequiredAtStates.get(curState);
        if (work == null) {
            work = 0;
        }
        return State.fresh().setWorkLeft(work).setProcessing(curState);
    }
}

package ca.bradj.questown.jobs.declarative;

import ca.bradj.questown.jobs.WorkSpot;
import ca.bradj.questown.jobs.WorkedSpot;
import ca.bradj.questown.logic.PredicateCollection;
import ca.bradj.questown.town.workstatus.State;
import com.google.common.collect.ImmutableMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

public abstract class AbstractWorkWI<POS, EXTRA, ITEM, TOWN> {

    private final ImmutableMap<Integer, Integer> workRequiredAtStates;
    private final BiFunction<EXTRA, Integer, @NotNull Integer> timeRequiredAtStates;
    private final ImmutableMap<Integer, PredicateCollection<ITEM, ?>> toolsRequiredAtStates;
    private final BiConsumer<EXTRA, WorkSpot<Integer, POS>> preStateChangeCallback;

    public <S> AbstractWorkWI(
            Map<Integer, Integer> workRequiredAtStates,
            BiFunction<EXTRA, Integer, Integer> timeRequiredAtStates,
            Map<Integer, ? extends @NotNull PredicateCollection<ITEM, S>> toolsRequiredAtStates,
            BiConsumer<EXTRA, WorkSpot<Integer, POS>> preStateChangeCallback
    ) {
        this.workRequiredAtStates = ImmutableMap.copyOf(workRequiredAtStates);
        this.timeRequiredAtStates = timeRequiredAtStates;
        this.toolsRequiredAtStates = ImmutableMap.copyOf(toolsRequiredAtStates);
        this.preStateChangeCallback = preStateChangeCallback;
    }

    public TOWN tryWork(
            EXTRA extra,
            WorkedSpot<POS> ws
    ) {
        POS bp = ws.workPosition();
        Integer curState = ws.stateAfterWork();
        Integer nextStepWork = workRequiredAtStates.getOrDefault(
                curState + 1, 0
        );
        if (nextStepWork == null) {
            nextStepWork = 0;
        }
        Integer nextStepTime = timeRequiredAtStates.apply(extra, curState + 1);
        TOWN updatedTown = applyWork(extra, bp, curState, nextStepWork, nextStepTime);
        boolean didWork = updatedTown != null;
        PredicateCollection<ITEM, ?> itemBooleanFunction = toolsRequiredAtStates.get(curState);
        if (didWork && itemBooleanFunction != null) {
            return degradeTool(extra, updatedTown, itemBooleanFunction);
        }
        return updatedTown;
    }

    protected abstract TOWN degradeTool(
            EXTRA extra,
            @Nullable TOWN tuwn,
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
        Integer work = workRequiredAtStates.get(curState);
        if (work == null) {
            work = 0;
        }
        return State.fresh().setWorkLeft(work).setProcessing(curState);
    }
}

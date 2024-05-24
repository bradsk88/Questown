package ca.bradj.questown.jobs;

import ca.bradj.questown.jobs.declarative.WithReason;

public interface NoisyBiPredicate<T1, T2> {
    WithReason<Boolean> test(T1 t1, T2 t2);
}

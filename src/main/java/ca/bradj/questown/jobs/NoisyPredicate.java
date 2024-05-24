package ca.bradj.questown.jobs;

import ca.bradj.questown.jobs.declarative.WithReason;

public interface NoisyPredicate<T> {
    WithReason<Boolean> test(T mcTownItem);
}

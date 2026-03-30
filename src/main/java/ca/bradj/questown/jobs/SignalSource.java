package ca.bradj.questown.jobs;

public interface SignalSource {
    Signals getSignal(Signals.DayTime dayTime);
}

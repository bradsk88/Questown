package ca.bradj.questown.jobs.declarative;

import ca.bradj.questown.jobs.IStatus;

import java.util.function.Function;

public class WithReason<S> {
    public final S value;
    public final String reason;

    /**
     * @deprecated For performance reasons, use the constructor which accepts
     * args (or use WithReason.unformatted(...) if you are not doing string
     * formatting on your "reason").
     */
    public WithReason(
            S value,
            String reason
    ) {
        this.value = value;
        this.reason = reason;
    }

    public WithReason(
            S value,
            String format,
            Object... args
    ) {
        this(value, String.format(format, args));
    }

    public static <X> WithReason<X> unformatted(X value, String message) {
        return new WithReason<>(value, message);
    }

    public static <X> X orNull(WithReason<X> newStatus) {
        if (newStatus == null) {
            return null;
        }
        return newStatus.value;
    }

    public static <STATUS extends IStatus<STATUS>> boolean isSame(
            STATUS value,
            WithReason<STATUS> withReason
    ) {
        if (withReason == null) {
            return value == null;
        }
        return withReason.value.equals(value);
    }

    public static WithReason<Boolean> bool(
            boolean test,
            String messageIfTrue,
            String messageIfFalse,
            Object... argsForBothMessages
    ) {
        if (test) {
            return new WithReason<>(true, messageIfTrue, argsForBothMessages);
        }
        return new WithReason<>(false, messageIfFalse, argsForBothMessages);
    }

    public WithReason<S> wrap(String s) {
        return new WithReason<>(
                value,
                String.format("%s: %s", s, reason)
        );
    }

    public <T> WithReason<T> map(Function<S, T> fn) {
        return new WithReason<>(fn.apply(value), reason);
    }

    public S value() {
        return value;
    }

    public String reason() {
        return reason;
    }
}

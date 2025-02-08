package ca.bradj.questown.core;

import com.google.common.collect.ImmutableList;

import java.util.function.Function;

public record Pair<A, B>(A a, B b) {
    public static <X> ImmutableList<X> toList(Pair<X, X> in) {
        return ImmutableList.of(in.a, in.b);
    }

    public static <X, Y> Pair<Y, Y> monoMap(
            Pair<X, X> in,
            Function<X, Y> mapper
    ) {
        return new Pair<>(mapper.apply(in.a), mapper.apply(in.b));
    }
}

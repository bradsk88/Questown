package ca.bradj.questown.core;

import com.google.common.collect.ImmutableList;

import java.util.Objects;
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

    public static <A, B> Pair<B, B> map(Pair<A, A> pair, Function<A, B> o) {
        return new Pair<>(o.apply(pair.a), o.apply(pair.b));
    }
    public static <A, B, C> Pair<A, C> mapB(Pair<A, B> pair, Function<B, C> o) {
        return new Pair<>(pair.a, o.apply(pair.b));
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        Pair<?, ?> pair = (Pair<?, ?>) o;
        return Objects.equals(a, pair.a) && Objects.equals(b, pair.b);
    }

    @Override
    public int hashCode() {
        return Objects.hash(a, b);
    }
}

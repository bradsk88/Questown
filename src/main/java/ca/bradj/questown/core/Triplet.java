package ca.bradj.questown.core;

import java.util.Objects;

public record Triplet<A, B, C>(A a, B b, C c) {
    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        Triplet<?, ?, ?> triplet = (Triplet<?, ?, ?>) o;
        return Objects.equals(a, triplet.a) && Objects.equals(b, triplet.b) && Objects.equals(
                c,
                triplet.c
        );
    }

    @Override
    public int hashCode() {
        return Objects.hash(a, b, c);
    }
}

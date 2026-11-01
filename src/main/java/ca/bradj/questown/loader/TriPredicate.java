package ca.bradj.questown.loader;

@FunctionalInterface
public interface TriPredicate<A, B, C> {
    boolean test(A a, B b, C c);

    static <A, B, C> TriPredicate<A, B, C> always() {
        return (a, b, c) -> true;
    }
}

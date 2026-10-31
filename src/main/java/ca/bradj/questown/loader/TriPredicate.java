package ca.bradj.questown.loader;

/**
 * A three-argument predicate: {@code (a, b, c) -> boolean}. Part of the neutral
 * loader facade so the main code does not depend on a loader's own small utility
 * types (this used to be {@code vazkii.patchouli.api.TriPredicate}).
 */
@FunctionalInterface
public interface TriPredicate<A, B, C> {
    boolean test(A a, B b, C c);

    static <A, B, C> TriPredicate<A, B, C> always() {
        return (a, b, c) -> true;
    }
}

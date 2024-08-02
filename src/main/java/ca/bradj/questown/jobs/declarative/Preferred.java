package ca.bradj.questown.jobs.declarative;

import java.util.Collection;

public record Preferred<T>(
        T preferredValue,
        Collection<T> alternates
) {
    public boolean isEmpty() {
        return preferredValue == null && alternates.isEmpty();
    }
}

package ca.bradj.questown.town.interfaces;

import java.util.function.Supplier;

public interface CacheFilter {
    <X> Supplier<X> doOrUseCache(Supplier<X> doer);
}

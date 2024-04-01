package ca.bradj.questown.jobs.production;

import com.google.common.base.Suppliers;

import java.util.function.Supplier;

public class ControlledCache<X> {

    private final Supplier<X> uncached;
    private final Supplier<X> cached;

    public ControlledCache(Supplier<X> fn) {
        this.cached = Suppliers.memoize(fn::get);
        this.uncached = fn;
    }

    public X get(
            boolean useCache
    ) {
        if (!useCache) {
            return uncached.get();
        }
        return cached.get();
    }

}

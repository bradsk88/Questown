package ca.bradj.questown.jobs.production;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import org.apache.commons.lang3.function.TriFunction;

public class ControlledTriCache<I1, I2, I3, OUT> {

    private final TriFunction<I1, I2, I3, OUT> uncached;

    public ControlledTriCache(TriFunction<I1, I2, I3, OUT> fn) {
        CacheLoader<Triplet<I1, I2, I3>, OUT> l = CacheLoader.from((trip) -> fn.apply(trip.i1, trip.i2, trip.i3));
        this.cache = CacheBuilder.newBuilder()
                                 .build(l);
        this.uncached = fn;
    }

    private record Triplet<I1, I2, I3>(
            I1 i1, I2 i2, I3 i3
    ) {
    }

    private final LoadingCache<Triplet<I1, I2, I3>, OUT> cache;

    public OUT get(
            boolean useCache,
            I1 i1,
            I2 i2,
            I3 i3
    ) {
        if (!useCache) {
            return uncached.apply(i1, i2, i3);
        }
        return cache.getUnchecked(new Triplet<>(i1, i2, i3));
    }
}

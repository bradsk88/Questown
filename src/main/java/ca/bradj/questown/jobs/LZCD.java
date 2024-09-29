package ca.bradj.questown.jobs;

import ca.bradj.questown.jobs.declarative.WithReason;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

public class LZCD<T> implements ILZCD<T> {

    public record Populated<T>(
            String name,
            @Nullable T value,
            Map<String, Object> conditions,
            Populated<T> ifCondFailOrNull
    ) {
    }

    public interface Dependency<T> extends Function<Supplier<T>, WithReason<Boolean>> {
        Populated<WithReason<@Nullable Boolean>> populate();

        String describe();

        String getName();
    }

    public final String name;
    private final ILZCD<T> wrapped;
    private final Collection<? extends ILZCD<Dependency<T>>> conditions;
    private final ILZCD<T> ifCondFail;
    protected @Nullable T value = null;

    public static <T> LZCD<T> oneDep(
            String name,
            ILZCD<T> wrapped,
            ILZCD<Dependency<T>> condition,
            ILZCD<T> ifCondFail
    ) {
        return new LZCD<>(name, wrapped, ImmutableList.of(condition), ifCondFail);
    }

    public LZCD(
            String name,
            ILZCD<T> wrapped,
            Collection<? extends ILZCD<Dependency<T>>> conditions,
            ILZCD<T> ifCondFail
    ) {
        this.name = name;
        this.wrapped = wrapped;
        this.conditions = ImmutableList.copyOf(conditions);
        this.ifCondFail = ifCondFail;
    }

    public static <T> LZCD<T> noDeps(String name, Supplier<T> o, Predicate<T> isNull) {
        return new LZCD<>(name, new ILZCD<T>() {
            private Populated<T> populated = null;
            private T val;

            @Override
            public boolean isValueNull(T val) {
                return isNull.test(val);
            }

            @Override
            public T resolve() {
                this.val = o.get();
                return val;
            }

            @Override
            public Populated<T> populate() {
                if (populated != null) {
                    return populated;
                }
                populated = new Populated<>(name, resolve(), ImmutableMap.of(), null);
                return populated;
            }
        }, ImmutableList.of(), leaf(() -> null, (v) -> true));
    }

    public static <T> ILZCD<T> leaf(Supplier<T> o, Predicate<T> isNull) {
        return new ILZCD<T>() {

            private Populated<T> populated = null;
            @Nullable
            T value = null;

            @Override
            public boolean isValueNull(T value) {
                return isNull.test(value);
            }

            @Override
            public T resolve() {
                return o.get();
            }

            @Override
            public Populated<T> populate() {
                if (populated != null) {
                    return populated;
                }
                value = resolve();
                populated = new Populated<>("value resolver", value, ImmutableMap.of(), null);
                return populated;
            }
        };
    }

    public T resolve() {
        if (!isValueNull(this.value)) {
            return this.value;
        }

        AtomicReference<T> cached = new AtomicReference<>();

        Supplier<T> cacher = () -> {
            if (cached.get() == null) {
                cached.set(wrapped.resolve());
            }
            return cached.get();
        };

        int condPassed = 0;
        for (ILZCD<Dependency<T>> d : conditions) {
            Dependency<T> resolve = d.resolve();
            if (resolve == null) {
                continue;
            }
            WithReason<Boolean> apply = resolve.apply(cacher);
            boolean v = apply.value();
            if (!v) {
                continue;
            }
            condPassed++;
        }
        if (condPassed < conditions.size()) {
            return ifCondFail.resolve();
        }
        this.value = cacher.get();
        if (value == null) {
            return ifCondFail.resolve();
        }
        return value;
    }

    public boolean isValueNull(T value) {
        return value == null;
    }


    public Populated<T> populate() {
        Map<String, Object> b = new HashMap<>();
        for (ILZCD<Dependency<T>> d : conditions) {
            d.populate();
            Dependency<T> resolve = d.resolve();
            Populated<WithReason<Boolean>> v = resolve.populate();
            b.put(resolve.describe(), v);
        }

        T resolve = wrapped.resolve();
        if (resolve == null) {
            b.put("wrapped value", "null (so fallback will be used)");
        }

        return new Populated<>(
                name, resolve,
                Collections.unmodifiableMap(b),
                ifCondFail.populate()
        );
    }

    @Override
    public String toString() {
        String v = value == null ? "<?>" : value.toString();
        if (conditions.isEmpty()) {
            return name + "=" + v;
        }
        return String.format(
                "(%s=%s) if [%s] else (%s)",
                name, v,
                String.join(",", conditions.stream().map(z -> {
                    Dependency<T> resolve = z.resolve();
                    if (resolve == null) {
                        return "null";
                    }
                    return resolve.describe();
                }).toList()),
                ifCondFail
        );
    }
}

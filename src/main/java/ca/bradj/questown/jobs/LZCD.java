package ca.bradj.questown.jobs;

import ca.bradj.questown.core.Pair;
import ca.bradj.questown.jobs.declarative.WithReason;
import com.google.common.collect.ImmutableList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;

public class LZCD<T> implements ILZCD<T> {

    public @Nullable T getValue() {
        return value;
    }

    public interface Dependency<T> extends Function<Supplier<T>, WithReason<Boolean>> {

        Populated<WithReason<@Nullable Boolean>> populate();

        String describe();

        String getName();
    }

    public final Pair<JobID, String> jobAndName;

    final ILZCD<T> wrapped;
    final Collection<? extends ILZCD<Dependency<T>>> conditions;
    final ILZCD<T> ifCondFail;
    private @Nullable T value = null;

    public LZCD(
            Pair<JobID, String> jobAndName,
            ILZCD<T> wrapped,
            Collection<? extends ILZCD<Dependency<T>>> conditions,
            ILZCD<T> ifCondFail
    ) {
        this.jobAndName = jobAndName;
        this.wrapped = wrapped;
        this.conditions = ImmutableList.copyOf(conditions);
        this.ifCondFail = ifCondFail;
    }

    @Override
    public void initializeAll() {
        this.value = null;
        this.wrapped.initializeAll();
        if (this.ifCondFail != null) {
            this.ifCondFail.initializeAll();
        }
        for (ILZCD<Dependency<T>> condition : this.conditions) {
            condition.initializeAll();
        }
    }

    public T resolve() {
        if (!isValueNull(this.value)) {
            return this.value;
        }

        Supplier<T> cacher = getValueWithCaching();

        int condPassed = checkConditions(cacher);
        if (condPassed < conditions.size()) {
            return ifCondFail.resolve();
        }

        T vv = this.setValue(cacher.get());
        if (vv == null) {
            return ifCondFail.resolve();
        }
        return vv;
    }

    private @NotNull Supplier<T> getValueWithCaching() {
        AtomicReference<T> cached = new AtomicReference<>();

        Supplier<T> cacher = () -> {
            if (cached.get() == null) {
                cached.set(wrapped.resolve());
            }
            return cached.get();
        }; // TODO: Encapsulate in function
        return cacher;
    }

    private int checkConditions(Supplier<T> cachedSelf) {
        int condPassed = 0;
        boolean v;
        for (ILZCD<Dependency<T>> d : conditions) {
            Dependency<T> resolve = d.resolve();
            if (resolve == null) {
                continue;
            }
            WithReason<Boolean> apply = resolve.apply(cachedSelf);
            v = apply.value();
            if (!v) {
                continue;
            }
            condPassed++;
        }
        return condPassed;
    }

    private T setValue(@Nullable T t) {
//        xyz = t;
        return t;
    }

    public boolean isValueNull(T value) {
        return value == null;
    }

    public Populated<T> populate() {
        Map<String, Object> b = new HashMap<>();
        for (ILZCD<Dependency<T>> d : conditions) {
            d.populate();
            Populated<Dependency<T>> resolve = d.populate();
            if (resolve.value() == null) {
                // TODO: Is this even possible?
                continue;
            }
            Populated<WithReason<Boolean>> v = resolve.value().populate();
            b.put(resolve.value().describe(), v);
        }

        T resolve = wrapped.resolve();
        if (resolve == null) {
            b.put("wrapped value", "null (so fallback will be used)");
        }

        return new Populated<>(jobAndName.b(), resolve, Collections.unmodifiableMap(b), ifCondFail.populate()) {
            @Override
            protected String stringRep() {
                return jobAndName.b(); // TODO: Good enough?
            }
        };
    }

    @Override
    public String toString() {
        String v = value == null ? "<?>" : value.toString();
        if (conditions.isEmpty()) {
            return jobAndName.b() + "=" + v;
        }
        return String.format(
                "(%s=%s) if [%s] else (%s)", jobAndName.b(), v, String.join(
                        ",", conditions.stream().map(z -> {
                            Populated<Dependency<T>> resolve = z.populate();
                            if (resolve == null) {
                                return "null";
                            }
                            return resolve.toString();
                        }).toList()
                ), ifCondFail
        );
    }

}

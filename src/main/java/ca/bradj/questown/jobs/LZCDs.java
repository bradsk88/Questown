package ca.bradj.questown.jobs;

import ca.bradj.questown.core.Pair;
import ca.bradj.questown.jobs.declarative.WithReason;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.jetbrains.annotations.Nullable;

import java.util.function.Predicate;
import java.util.function.Supplier;

public class LZCDs {
    public static <T> LZCD<T> oneDep(
            Pair<JobID, String> name,
            ILZCD<T> wrapped,
            ILZCD<LZCD.Dependency<T>> condition,
            ILZCD<T> ifCondFail
    ) {
        return new LZCD<>(name, wrapped, ImmutableList.of(condition), ifCondFail);
    }

    public static <T> LZCD<T> noDeps(
            Pair<JobID, String> name,
            Supplier<T> o,
            Predicate<T> isNull
    ) {
        return new LZCD<>(
                name, new ILZCD<T>() {
            private Populated<T> populated = null;
            private T val;

            @Override
            public void initializeAll() {
                val = null;
                populated = null;
            }

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
                populated = new Populated<>(name.toString(), resolve(), ImmutableMap.of(), null) {
                    @Override
                    protected String stringRep() {
                        return name + " [no dependencies]";
                    }
                };
                return populated;
            }
        }, ImmutableList.of(), leaf(() -> null, (v) -> true)
        );
    }

    public static <T> ILZCD<T> leaf(
            Supplier<T> o,
            Predicate<T> isNull
    ) {
        return new ILZCD<T>() {

            private Populated<T> populated = null;
            @Nullable T value = null;

            @Override
            public void initializeAll() {
                value = null;
            }

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
                populated = new Populated<>("value resolver", value, ImmutableMap.of(), null) {
                    @Override
                    protected String stringRep() {
                        return "leaf node [" + value + "]";
                    }
                };
                return populated;
            }

            @Override
            public String toString() {
                return "leaf node [" + (value == null ? "<?>" : value) + "]";
            }
        };
    }

    public static LZCD.Dependency<Void> invert(LZCD.Dependency<Void> voidDependency) {
        return new SimpleDependency(voidDependency.getName() + " (result inverted)") {
            @Override
            protected Populated<WithReason<Boolean>> doPopulate(boolean stopOnTrue) {
                Populated<WithReason<Boolean>> p = voidDependency.populate();
                return new Populated<>(p.name(), p.value().map(v -> !v).wrap("inverted"), p.conditions(), p.ifCondFailOrNull()) {
                    @Override
                    protected String stringRep() {
                        return "inverted value of " + voidDependency.getName();
                    }
                };
            }

            @Override
            public String describe() {
                return voidDependency.describe() + "(result inverted)";
            }
        };
    }
}

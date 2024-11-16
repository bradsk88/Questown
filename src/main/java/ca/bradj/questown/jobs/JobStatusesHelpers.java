package ca.bradj.questown.jobs;

import ca.bradj.questown.jobs.declarative.WithReason;
import com.google.common.collect.ImmutableMap;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

public class JobStatusesHelpers {

    public static class PrePopDep<T> implements LZCD.Dependency<T> {

        private final Supplier<Boolean> s;
        private WithReason<Boolean> value;

        public PrePopDep(String name, Supplier<Boolean> s) {
            this.name = name;
            this.s = s;
        }

        private final String name;

        @Override
        public LZCD.Populated<WithReason<@Nullable Boolean>> populate() {
            this.value = WithReason.always(s.get(), "input");
            return new LZCD.Populated<>(
                    name,
                    value,
                    ImmutableMap.of(),
                    null
            );
        }

        @Override
        public String describe() {
            String v = value == null ? "<?>" : value.toString();
            return name + '=' + v;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public WithReason<Boolean> apply(Supplier<T> tSupplier) {
            populate();
            return this.value;
        }
    }
}

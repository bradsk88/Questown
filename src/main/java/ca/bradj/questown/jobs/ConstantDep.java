package ca.bradj.questown.jobs;

import ca.bradj.questown.jobs.declarative.WithReason;
import com.google.common.collect.ImmutableMap;

public class ConstantDep extends SimpleDependency {
    private final Populated<WithReason<Boolean>> value;

    public ConstantDep(
            String name,
            boolean bVal
    ) {
        super(name);
        this.value = new Populated<>(
                "test supplies",
                WithReason.always(bVal, "input"),
                ImmutableMap.of(),
                null
        ) {
            @Override
            protected String stringRep() {
                return "Constant [" + bVal + "]";
            }
        };
    }

    @Override
    protected Populated<WithReason<Boolean>> doPopulate(boolean stopOnTrue) {
        return value;
    }

    @Override
    public String describe() {
        return "constant: " + getName();
    }
}

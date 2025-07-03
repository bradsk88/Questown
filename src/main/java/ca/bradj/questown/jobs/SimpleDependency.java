package ca.bradj.questown.jobs;

import ca.bradj.questown.jobs.declarative.WithReason;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

public abstract class SimpleDependency implements LZCD.Dependency<Void> {
    public SimpleDependency(String name) {
        this.name = name;
    }

    private final String name;

    @Override
    public Populated<WithReason<@Nullable Boolean>> populate() {
        return doPopulate(false);
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public WithReason<Boolean> apply(Supplier<Void> voidSupplier) {
        Populated<WithReason<Boolean>> pop = doPopulate(true);
        return pop.value();
    }

    protected abstract Populated<WithReason<Boolean>> doPopulate(boolean stopOnTrue);
}

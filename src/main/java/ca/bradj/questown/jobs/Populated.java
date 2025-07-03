package ca.bradj.questown.jobs;

import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Objects;

public abstract class Populated<T> {
    private final String name;
    private final @Nullable T value;
    private final Map<String, Object> conditions;
    private final Populated<T> ifCondFailOrNull;

    public Populated(
            String name,
            @Nullable T value,
            Map<String, Object> conditions,
            Populated<T> ifCondFailOrNull
    ) {
        this.name = name;
        this.value = value;
        this.conditions = conditions;
        this.ifCondFailOrNull = ifCondFailOrNull;
    }

    public String name() {
        return name;
    }

    public @Nullable T value() {
        return value;
    }

    public Map<String, Object> conditions() {
        return conditions;
    }

    public Populated<T> ifCondFailOrNull() {
        return ifCondFailOrNull;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (Populated) obj;
        return Objects.equals(this.name, that.name) && Objects.equals(
                this.value,
                that.value
        ) && Objects.equals(this.conditions, that.conditions) && Objects.equals(
                this.ifCondFailOrNull,
                that.ifCondFailOrNull
        );
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, value, conditions, ifCondFailOrNull);
    }

    @Override
    public String toString() {
        return stringRep();
    }

    protected abstract String stringRep();

}

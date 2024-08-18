package ca.bradj.questown.jobs;

import java.util.Objects;

public record WorkPosition<POS>(
        POS jobBlock,
        POS entityFeetPos
) {

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        WorkPosition<?> that = (WorkPosition<?>) o;
        return Objects.equals(jobBlock, that.jobBlock) && Objects.equals(entityFeetPos, that.entityFeetPos);
    }

    @Override
    public int hashCode() {
        return Objects.hash(jobBlock, entityFeetPos);
    }
}

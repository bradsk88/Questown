package ca.bradj.questown.core.space;

import java.util.Objects;

// InclusiveSpace is defined by two corner positions. An inclusive space is the
// cube between the two positions, up to the outermost corner of those corner blocks.
public class InclusiveSpace {

    public InclusiveSpace(
            Position aa,
            Position bb
    ) {
        this.aa = aa;
        this.bb = bb;
    }

    private final Position aa;
    private final Position bb;

    @Override
    public String toString() {
        return "BoundingBox{" +
                "aa=" + aa +
                ", bb=" + bb +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        InclusiveSpace that = (InclusiveSpace) o;
        return Objects.equals(aa, that.aa) && Objects.equals(bb, that.bb);
    }

    @Override
    public int hashCode() {
        return Objects.hash(aa, bb);
    }

    public Position getCornerA() {
        return aa;
    }

    public Position getCornerB() {
        return bb;
    }
}

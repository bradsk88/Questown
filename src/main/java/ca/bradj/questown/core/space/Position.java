package ca.bradj.questown.core.space;

import java.util.Objects;

public class Position {
    public Position(
            int x,
            int y,
            int z
    ) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public final int x;
    public final int y;
    public final int z;

    @Override
    public String toString() {
        return "DoorPos{" +
                "x=" + x +
                ", y=" + y +
                ", z=" + z +
                '}';
    }

    public Position offset(
            int x,
            int y,
            int z
    ) {
        return new Position(this.x + x, this.y + y, this.z + z);
    }

    public Position WithX(int x) {
        return new Position(x, this.y, this.z);
    }

    public Position WithZ(int z) {
        return new Position(this.x, this.y, z);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Position doorPos = (Position) o;
        return x == doorPos.x && y == doorPos.y && z == doorPos.z;
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, y, z);
    }
}

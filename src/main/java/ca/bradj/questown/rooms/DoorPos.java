package ca.bradj.questown.rooms;

import java.util.Objects;

public class DoorPos {
    public DoorPos(
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

    public DoorPos offset(
            int x,
            int y,
            int z
    ) {
        return new DoorPos(this.x + x, this.y + y, this.z + z);
    }

    public DoorPos WithX(int x) {
        return new DoorPos(x, this.y, this.z);
    }

    public DoorPos WithZ(int z) {
        return new DoorPos(this.x, this.y, z);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DoorPos doorPos = (DoorPos) o;
        return x == doorPos.x && y == doorPos.y && z == doorPos.z;
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, y, z);
    }
}

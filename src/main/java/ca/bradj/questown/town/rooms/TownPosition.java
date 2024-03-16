package ca.bradj.questown.town.rooms;

import ca.bradj.roomrecipes.core.space.Position;
import net.minecraft.core.BlockPos;

import java.util.Objects;

public class TownPosition {
    public final int x;
    public final int z;
    public final int scanLevel;

    public TownPosition(int x, int z, int scanLevel) {
        this.x = x;
        this.z = z;
        this.scanLevel = scanLevel;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TownPosition that = (TownPosition) o;
        return x == that.x && z == that.z && scanLevel == that.scanLevel;
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, z, scanLevel);
    }

    @Override
    public String toString() {
        return "TownPosition{" +
                "x=" + x +
                ", z=" + z +
                ", scanLevel=" + scanLevel +
                '}';
    }

    public Position toPosition() {
        return new Position(x, z);
    }

    public int getY(int flagY) {
        return flagY + scanLevel;
    }
}

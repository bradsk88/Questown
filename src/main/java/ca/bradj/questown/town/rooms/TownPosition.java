package ca.bradj.questown.town.rooms;

public class TownPosition {
    public final int x;
    public final int z;
    public final int scanLevel;

    public TownPosition(int x, int z, int scanLevel) {
        this.x = x;
        this.z = z;
        this.scanLevel = scanLevel;
    }
}

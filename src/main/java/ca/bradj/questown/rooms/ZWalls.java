package ca.bradj.questown.rooms;

public class ZWalls {
    public final ZWall westWall;
    public final ZWall eastWall;

    public ZWalls(
            ZWall westWall,
            ZWall eastWall
    ) {
        this.westWall = westWall;
        this.eastWall = eastWall;
    }
}

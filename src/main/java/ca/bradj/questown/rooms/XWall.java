package ca.bradj.questown.rooms;

public class XWall {
    public final DoorPos westCorner;
    public final DoorPos eastCorner;

    public XWall(
            DoorPos westCorner,
            DoorPos eastCorner
    ) {
        this.westCorner = westCorner;
        this.eastCorner = eastCorner;
    }
}

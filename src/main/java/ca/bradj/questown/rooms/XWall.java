package ca.bradj.questown.rooms;

import ca.bradj.questown.core.space.Position;

public class XWall {
    public final Position westCorner;
    public final Position eastCorner;

    public XWall(
            Position westCorner,
            Position eastCorner
    ) {
        this.westCorner = westCorner;
        this.eastCorner = eastCorner;
    }
}

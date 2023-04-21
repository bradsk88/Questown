package ca.bradj.questown.rooms;

import ca.bradj.questown.core.space.Position;

public class XWall {
    public final Position westCorner;
    public final Position eastCorner;

    public XWall(
            Position westCorner,
            Position eastCorner
    ) {
        Position wc = westCorner;
        Position ec = eastCorner;
        if (wc.x > ec.x) {
            wc = eastCorner;
            ec = westCorner;
        }
        this.westCorner = wc;
        this.eastCorner = ec;
    }

    public int getLength() {
        return this.eastCorner.x - this.westCorner.x;
    }

    public XWall shortenWestEnd(int i) {
        return new XWall(this.westCorner.WithX(this.westCorner.x + 1), this.eastCorner);
    }

    public XWall shortenEastEnd(int i) {
        return new XWall(this.westCorner, this.eastCorner.WithX(this.eastCorner.x - 1));
    }
}

package ca.bradj.questown.render;

import ca.bradj.questown.core.space.InclusiveSpace;
import ca.bradj.questown.core.space.Position;

public class RoomEffects {

    public interface ParticleAdder {
        void addParticle(
                double x,
                double y,
                double z
        );
    }

    public static void renderParticlesBetween(
            InclusiveSpace space,
            ParticleAdder pa
    ) {
        Position pos1 = space.getCornerA();
        Position pos2 = space.getCornerB();
        double minX = Math.min(pos1.x, pos2.x);
        double minY = Math.min(pos1.y, pos2.y);
        double minZ = Math.min(pos1.z, pos2.z);
        double maxX = Math.max(pos1.x, pos2.x);
        double maxY = Math.max(pos1.y, pos2.y);
        double maxZ = Math.max(pos1.z, pos2.z);

        for (double x = minX; x <= maxX; x++) {
            for (double y = minY; y <= maxY; y++) {
                for (double z = minZ; z <= maxZ; z++) {
                    pa.addParticle(x + 0.5D, y + 0.5D, z + 0.5D);
                }
            }
        }
    }
}

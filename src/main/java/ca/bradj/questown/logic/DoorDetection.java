package ca.bradj.questown.logic;

import ca.bradj.questown.core.space.Position;
import com.google.common.collect.ImmutableList;

import java.util.Collection;

public class DoorDetection {

    public interface DoorChecker {
        boolean IsDoor(Position pos);
    }

    public static Collection<Position> LocateDoorsAroundPosition(
            Position pos,
            DoorChecker checker,
            int radius
    ) {
        // TODO: Use smarter algorithms to increase performance?
        ImmutableList.Builder<Position> dps = ImmutableList.builder();
        for (int z = -radius; z < radius; z++) {
            for (int x = -radius; x < radius; x++) {
                Position dp = new Position(pos.x + x, pos.y, pos.z + z);
                if (checker.IsDoor(dp)) {
                    dps.add(dp);
                }
            }
        }
        return dps.build();
    }


}

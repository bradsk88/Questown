package ca.bradj.questown.logic;

import ca.bradj.questown.rooms.DoorPos;
import com.google.common.collect.ImmutableList;

import java.util.Collection;

public class DoorFinder {

    public interface DoorChecker {
        boolean IsDoor(DoorPos pos);
    }

    public static Collection<DoorPos> LocateDoorsAroundPosition(
            DoorPos pos,
            DoorChecker checker,
            int radius
    ) {
        // TODO: Use smarter algorithms to increase performance?
        ImmutableList.Builder<DoorPos> dps = ImmutableList.builder();
        for (int z = -radius; z < radius; z++) {
            for (int x = -radius; x < radius; x++) {
                DoorPos dp = new DoorPos(pos.x + x, pos.y, pos.z + z);
                if (checker.IsDoor(dp)) {
                    dps.add(dp);
                }
            }
        }
        return dps.build();
    }


}

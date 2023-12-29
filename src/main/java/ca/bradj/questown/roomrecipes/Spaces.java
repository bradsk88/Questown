package ca.bradj.questown.roomrecipes;

import ca.bradj.roomrecipes.adapter.Positions;
import ca.bradj.roomrecipes.core.space.InclusiveSpace;
import ca.bradj.roomrecipes.serialization.MCRoom;
import com.google.common.collect.ImmutableList;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.NotNull;

public class Spaces {
    @NotNull
    public static MCRoom metaRoomAround(BlockPos p, int radius) {
        return new MCRoom(
                Positions.FromBlockPos(p.offset(1, 0, 0)),
                ImmutableList.of(new InclusiveSpace(
                        Positions.FromBlockPos(p).offset(-radius, -radius),
                        Positions.FromBlockPos(p).offset(radius, radius)
                )),
                p.getY()
        );
    }
}

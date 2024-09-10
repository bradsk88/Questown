package ca.bradj.questown.jobs.special;

import ca.bradj.questown.integration.jobs.BeforeInitEvent;
import ca.bradj.questown.integration.jobs.BeforeTickEvent;
import ca.bradj.questown.integration.jobs.JobPhaseModifier;
import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.roomrecipes.core.space.Position;
import ca.bradj.roomrecipes.logic.InclusiveSpaces;
import ca.bradj.roomrecipes.serialization.MCRoom;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public class WorkSpotFromHeldItemSpecialRule extends
        JobPhaseModifier {

    @Override
    public void beforeTick(BeforeTickEvent bxEvent) {
        super.beforeTick(bxEvent);
        @Nullable MCRoom room = getRoomFromHeldItems(bxEvent.heldItems());
        @Nullable BlockPos pos = getJobBlockPositionFromHeldItems(bxEvent.heldItems());

        bxEvent.replaceRoomCheck().accept(before -> () -> {
            ImmutableMap.Builder<Integer, Collection<MCRoom>> b = ImmutableMap.builder();
            b.put(bxEvent.getJobBlockState().apply(pos).processingState(), ImmutableList.of(room));
            return b.build();
        });
    }

    @Override
    public void beforeInit(BeforeInitEvent bxEvent) {
        super.beforeInit(bxEvent);

        bxEvent.jobBlockCheckReplacer().accept(old -> {
            @NotNull BlockPos room = getJobBlockPositionFromHeldItems(bxEvent.heldItems().get());
            return (sl, bp) -> room.equals(bp);
        });
    }

    private static @NotNull MCRoom getRoomFromHeldItems(ImmutableList<MCHeldItem> mcHeldItems) {
//        for (MCHeldItem i : bxEvent.heldItems()) {
        // TODO[ASAP]: Get room from item
//        }
        return new MCRoom(
                new Position(-538, -525),
                ImmutableList.of(InclusiveSpaces.from(-538, -527).to(-536, -524)),
                63
        );
    }

    private static @NotNull BlockPos getJobBlockPositionFromHeldItems(ImmutableList<MCHeldItem> mcHeldItems) {
//        for (MCHeldItem i : bxEvent.heldItems()) {
        // TODO[ASAP]: Get room from item
//        }
        return new BlockPos(-537, 63, -526);
    }
}

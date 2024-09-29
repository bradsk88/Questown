package ca.bradj.questown.jobs.special;

import ca.bradj.questown.integration.jobs.BeforeInitEvent;
import ca.bradj.questown.integration.jobs.BeforeTickEvent;
import ca.bradj.questown.integration.jobs.JobPhaseModifier;
import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.items.StockRequestItem;
import ca.bradj.roomrecipes.serialization.MCRoom;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public class WorkSpotFromHeldItemSpecialRule extends
        JobPhaseModifier {

    @Override
    public void beforeTick(BeforeTickEvent bxEvent) {
        super.beforeTick(bxEvent);
        @Nullable MCRoom room = getRoomFromHeldItems(bxEvent.heldItems());
        @Nullable BlockPos pos = getJobBlockPositionFromHeldItems(bxEvent.heldItems());

        if (room == null || pos == null) {
            return;
        }

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
            @Nullable BlockPos room = getJobBlockPositionFromHeldItems(bxEvent.heldItems().get());
            if (room == null) {
                return old;
            }
            return (sl, bp) -> room.equals(bp);
        });
    }

    private static @Nullable MCRoom getRoomFromHeldItems(ImmutableList<MCHeldItem> mcHeldItems) {
        for (MCHeldItem i : mcHeldItems) {
            if (i.get().get() instanceof StockRequestItem) {
                @Nullable MCRoom room = StockRequestItem.getRoom(i.getItemNBTData());
                if (room != null) {
                    return room;
                }
            }
        }
        return null;
    }

    private static @Nullable BlockPos getJobBlockPositionFromHeldItems(ImmutableList<MCHeldItem> mcHeldItems) {
        for (MCHeldItem i : mcHeldItems) {
            if (i.get().get() instanceof StockRequestItem) {
                @Nullable BlockPos room = StockRequestItem.getJobBlock(i);
                if (room != null) {
                    return room;
                }
            }
        }
        return null;
    }
}

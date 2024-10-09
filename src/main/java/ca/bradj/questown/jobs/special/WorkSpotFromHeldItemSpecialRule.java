package ca.bradj.questown.jobs.special;

import ca.bradj.questown.core.UtilClean;
import ca.bradj.questown.integration.jobs.BeforeInitEvent;
import ca.bradj.questown.integration.jobs.BeforeTickEvent;
import ca.bradj.questown.integration.jobs.JobPhaseModifier;
import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.items.StockRequestItem;
import ca.bradj.questown.jobs.production.RoomsNeedingIngredientsOrTools;
import ca.bradj.roomrecipes.adapter.IRoomRecipeMatch;
import ca.bradj.roomrecipes.serialization.MCRoom;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

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

        bxEvent.replaceRoomCheck().accept(before -> {
            Map<Integer, Collection<IRoomRecipeMatch<MCRoom, ResourceLocation, BlockPos, ?>>> b = new HashMap<>();
            int state = bxEvent.getJobBlockState().apply(pos).processingState();
            Collection<IRoomRecipeMatch<MCRoom, ResourceLocation, BlockPos, ?>> stateRooms = UtilClean.getOrDefaultCollection(
                    b, state, new ArrayList<>(), true
            );
            stateRooms.add(match(bxEvent, pos, room));
            b.put(state, ImmutableList.copyOf(stateRooms));
            return new RoomsNeedingIngredientsOrTools<>(ImmutableMap.copyOf(b));
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
            return room::equals;
        });

        bxEvent.supplyRoomCheckReplacer().accept(old -> {
            @Nullable MCRoom room = getRoomFromHeldItems(bxEvent.heldItems().get());
            if (room == null) {
                return old;
            }
            // Exclude the work spot as a supply source
            return m -> !m.room.equals(room);
        });
    }

    private static @NotNull IRoomRecipeMatch<MCRoom, ResourceLocation, BlockPos, Object> match(
            BeforeTickEvent bxEvent,
            @Nullable BlockPos pos,
            @Nullable MCRoom room
    ) {
        return new IRoomRecipeMatch<>() {
            @Override
            public ResourceLocation getRecipeID() {
                return bxEvent.locInfo().baseRoom();
            }

            @Override
            public MCRoom getRoom() {
                return room;
            }

            @Override
            public ImmutableMap<BlockPos, Object> getContainedBlocks() {
                return ImmutableMap.of(pos, true);
            }
        };
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
                @Nullable BlockPos room = StockRequestItem.getJobBlock(i.getItemNBTData());
                if (room != null) {
                    return room;
                }
            }
        }
        return null;
    }
}

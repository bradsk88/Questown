package ca.bradj.questown.jobs.special;

import ca.bradj.questown.core.UtilClean;
import ca.bradj.questown.integration.jobs.AfterDropLootEvent;
import ca.bradj.questown.integration.jobs.BeforeInitEvent;
import ca.bradj.questown.integration.jobs.BeforeTickEvent;
import ca.bradj.questown.integration.jobs.JobPhaseModifier;
import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.items.StockRequestItem;
import ca.bradj.questown.jobs.production.RoomsNeedingVillagerInput;
import ca.bradj.questown.jobs.production.RoomsNeedingVillagerInput.NVIRoom;
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
            Map<Integer, Collection<NVIRoom<MCRoom, ResourceLocation, BlockPos>>> b = new HashMap<>();
            int state = bxEvent.getJobBlockState().apply(pos).processingState();
            Collection<NVIRoom<MCRoom, ResourceLocation, BlockPos>> stateRooms = UtilClean.getOrDefaultCollection(
                    b, state, new ArrayList<>(), true
            );
            stateRooms.add(match(bxEvent, pos, room));
            b.put(state, ImmutableList.copyOf(stateRooms));
            return new RoomsNeedingVillagerInput<>(ImmutableMap.copyOf(b));
        });
    }

    @Override
    public void beforeInit(BeforeInitEvent bxEvent) {
        super.beforeInit(bxEvent);

        bxEvent.jobBlockCheckReplacer().accept((old) -> (ctx) -> {
            @Nullable BlockPos block = getJobBlockPositionFromHeldItems(ctx.heldItems().get());
            if (block == null) {
                return old.test(ctx);
            }
            return block.equals(ctx.blockPos());
        });

        bxEvent.supplyRoomCheckReplacer().accept(old -> (heldItems, match) -> {
            @Nullable MCRoom room = getRoomFromHeldItems(heldItems);
            if (room == null) {
                return old.test(heldItems, match);
            }
            // Exclude the work spot as a supply source
            return !match.room.equals(room);
        });
    }

    private static @NotNull NVIRoom<MCRoom, ResourceLocation, BlockPos> match(
            BeforeTickEvent bxEvent,
            @Nullable BlockPos pos,
            @Nullable MCRoom room
    ) {
        return new NVIRoom<>(new IRoomRecipeMatch<>() {

            @Override
            public ImmutableList<ResourceLocation> getRecipeIDs() {
                return ImmutableList.of(bxEvent.locInfo().baseRoom());
            }

            @Override
            public MCRoom getRoom() {
                return room;
            }

            @Override
            public ImmutableMap<BlockPos, Object> getContainedBlocks() {
                return ImmutableMap.of(pos, true);
            }
        }, false);
    }

    public static @Nullable MCRoom getRoomFromHeldItems(Collection<MCHeldItem> mcHeldItems) {
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

    public static @Nullable BlockPos getJobBlockPositionFromHeldItems(Collection<MCHeldItem> mcHeldItems) {
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

    @Override
    public <CONTEXT> @Nullable CONTEXT afterDropLoot(
            CONTEXT ctxInput,
            AfterDropLootEvent event
    ) {
        CONTEXT ctx = super.afterDropLoot(ctxInput, event);
        ArrayList<MCHeldItem> itemsDropped = new ArrayList<>();
        for (MCHeldItem mcHeldItem : event.itemsBeforeDrop()) {
            if (event.itemsAfterDrop().contains(mcHeldItem)) {
                continue;
            }
            itemsDropped.add(mcHeldItem);
        }
        @Nullable BlockPos jbp = getJobBlockPositionFromHeldItems(itemsDropped);
        if (jbp == null) {
            return ctx;
        }
        event.clearStatus().accept(jbp);
        return ctx;
    }
}

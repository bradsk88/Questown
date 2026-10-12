package ca.bradj.questown.town.entity;

import ca.bradj.questown.blocks.TownFlagSubBlocks;
import ca.bradj.questown.jobs.requests.WorkRequest;
import ca.bradj.questown.town.quests.MCQuest;
import ca.bradj.questown.town.quests.Quest;
import ca.bradj.questown.town.special.SpecialQuests;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * F2 board behaviour, unit-level:
 *
 * <ul>
 *   <li>{@link TownWorkHandle#requestWork(WorkRequest)} is idempotent - re-requesting an item
 *       that already has a pending request does not stack a duplicate (the 2026-10-12 playtest:
 *       "I don't want to make that many; I only want to request X").</li>
 *   <li>The day-2 "first work request" quest shape ({@link MCQuest#firstWorkRequest}): type,
 *       wanted id, not complete at creation.</li>
 * </ul>
 *
 * Bootstraps vanilla so a real {@code Item} can back the request (registry-bound data; see
 * PostDowntimeWarperNoOpGuardTest for the pattern).
 */
class TownWorkHandleRequestWorkTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static TownWorkHandle newHandle() {
        return new TownWorkHandle(new TownFlagSubBlocks(BlockPos.ZERO), BlockPos.ZERO);
    }

    @Test
    void requestWork_addsAndNotifies() {
        TownWorkHandle h = newHandle();
        List<TownWorkHandle.Change> changes = new ArrayList<>();
        h.addChangeListener(changes::add);

        h.requestWork(WorkRequest.of(Items.APPLE));

        assertEquals(1, h.getRequestedResults().size());
        assertEquals(1, changes.size());
        assertEquals(null, changes.get(0).removed());
    }

    @Test
    void requestWork_duplicateIsNoOp() {
        TownWorkHandle h = newHandle();
        List<TownWorkHandle.Change> changes = new ArrayList<>();
        h.addChangeListener(changes::add);

        h.requestWork(WorkRequest.of(Items.APPLE));
        h.requestWork(WorkRequest.of(Items.APPLE));

        assertEquals(1, h.getRequestedResults().size(),
                "a duplicate pending request must not stack a second copy on the board");
        assertEquals(1, changes.size(), "a no-op re-request must not fire a board change");
    }

    @Test
    void requestWork_removedRequestCanBeReRequested() {
        TownWorkHandle h = newHandle();
        List<TownWorkHandle.Change> changes = new ArrayList<>();
        h.addChangeListener(changes::add);

        h.requestWork(WorkRequest.of(Items.APPLE));
        h.removeWorkRequest(WorkRequest.of(Items.APPLE));
        h.requestWork(WorkRequest.of(Items.APPLE));

        assertEquals(1, h.getRequestedResults().size(),
                "once the pending request is fulfilled (removed), re-requesting is allowed");
        assertEquals(3, changes.size());
        assertEquals(null, changes.get(0).removed());
        assertEquals(WorkRequest.of(Items.APPLE), changes.get(1).removed());
        assertEquals(null, changes.get(2).removed());
    }

    @Test
    void firstWorkRequest_questShape() {
        UUID batch = UUID.randomUUID();
        MCQuest q = MCQuest.firstWorkRequest(batch, null);

        assertEquals(Quest.QuestType.FIRST_WORK_REQUEST, q.getType());
        assertEquals(SpecialQuests.FIRST_WORK_REQUEST, q.getWantedId());
        assertFalse(q.isComplete());
        assertNotNull(q.getFlavorText(), "the quest must carry player-facing guidance");
    }
}

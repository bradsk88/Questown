package ca.bradj.questown.jobs.special;

import ca.bradj.questown.integration.jobs.BeforeTickEvent;
import ca.bradj.questown.integration.jobs.JobPhaseModifier;
import ca.bradj.roomrecipes.core.space.Position;
import ca.bradj.roomrecipes.logic.InclusiveSpaces;
import ca.bradj.roomrecipes.serialization.MCRoom;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public class WorkSpotFromHeldItemSpecialRule extends
        JobPhaseModifier {

    @Override
    public void beforeTick(BeforeTickEvent bxEvent) {
        super.beforeTick(bxEvent);
        @Nullable MCRoom room = new MCRoom(new Position(0, 0), ImmutableList.of(InclusiveSpaces.from(0, 0).to(1, 1)), 1);
//        for (MCHeldItem i : bxEvent.heldItems()) {
        // TODO: Get room from item
//        }

        bxEvent.replaceRoomCheck().accept(before -> () -> {
            ImmutableMap.Builder<Integer, Collection<MCRoom>> b = ImmutableMap.builder();
            bxEvent.states().forEach(state -> {
                b.put(state, ImmutableList.of(room));
            });
            return b.build();
        });
    }
}

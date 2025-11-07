package ca.bradj.questown.integration;

import ca.bradj.questown.QT;
import ca.bradj.questown.integration.jobs.BeforeFindJobSiteEvent;
import ca.bradj.questown.integration.jobs.BeforeMoveToNextStateEvent;
import ca.bradj.questown.integration.jobs.BeforeTickEvent;
import ca.bradj.questown.integration.jobs.JobPhaseModifier;
import ca.bradj.questown.jobs.declarative.WithReason;
import ca.bradj.questown.mc.Compat;
import com.google.common.collect.ImmutableList;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

public class RandomWorkSpot extends JobPhaseModifier {

    // TODO: Consider namespacing these to the rule's class name to isolate rules from each other.
    private final String DATA_KEY_WORKSPOT_OVERRIDE = "questown:work_spot_override";

    private final boolean preferSocial;

    public RandomWorkSpot(boolean preferSocial) {
        super();
        this.preferSocial = preferSocial;
    }

    @Override
    public void beforeTick(BeforeTickEvent bxEvent) {
        super.beforeTick(bxEvent);
        if (!bxEvent.firstTick()) {
            return;
        }
        ServerLevel serverLevel = bxEvent.level().get();
        choosePosAndStoreOnVillager(bxEvent.otherVillagerPositions(), bxEvent.randomWalkableTownPosition(), bxEvent.writeUnsafeDataToVillager(), serverLevel);
    }

    private void choosePosAndStoreOnVillager(
            java.util.function.Supplier<ImmutableList<BlockPos>> otherVillagerPositions,
            java.util.function.Supplier<BlockPos> randomWalkableTownPosition,
            java.util.function.BiConsumer<String, String> writeUnsafeDataToVillager,
            ServerLevel serverLevel
    ) {
        if (preferSocial && Compat.getRandomBool(serverLevel)) {
            ImmutableList<BlockPos> ovp = otherVillagerPositions.get();
            if (!ovp.isEmpty()) {
                BlockPos pos = Compat.shuffle(ovp, serverLevel).get(0);
                storePosOnVillager(writeUnsafeDataToVillager, pos);
                return;
            }
        }
        BlockPos p = randomWalkableTownPosition.get();
        storePosOnVillager(writeUnsafeDataToVillager, p);
    }

    private void storePosOnVillager(
            java.util.function.BiConsumer<String, String> writeUnsafeDataToVillager,
            BlockPos p
    ) {
        String val = Long.toString(p.asLong());
        writeUnsafeDataToVillager.accept(DATA_KEY_WORKSPOT_OVERRIDE, val);
    }

    @Override
    public void beforeFindJobSite(BeforeFindJobSiteEvent event) {
        super.beforeFindJobSite(event);
        String posStr = event.getUnsafeDataFromVillager().apply(DATA_KEY_WORKSPOT_OVERRIDE);
        if (posStr == null || posStr.isEmpty()) {
            QT.logBug("RandomWorkSpot: No work spot override data found.");
            return;
        }
        BlockPos override = BlockPos.of(Long.parseLong(posStr));
        event.applyWorkspotOverride().accept(WithReason.always(override, "Overridden by " + this.getClass().getSimpleName()));
    }

    @Override
    public Void beforeMoveToNextState(BeforeMoveToNextStateEvent event) {
        Void unused = super.beforeMoveToNextState(event);
        choosePosAndStoreOnVillager(
                event.otherVillagerPositions(),
                event.randomWalkableTownPosition(),
                event.writeUnsafeDataToVillager(),
                event.level().get()
        );
        return unused;
    }
}

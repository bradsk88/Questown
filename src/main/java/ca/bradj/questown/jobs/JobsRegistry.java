package ca.bradj.questown.jobs;

import ca.bradj.questown.QT;
import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.jobs.blacksmith.BlacksmithWoodenPickaxeJob;
import ca.bradj.questown.jobs.production.ProductionStatus;
import ca.bradj.questown.jobs.smelter.DSmelterJob;
import ca.bradj.questown.mobs.visitor.GathererJob;
import ca.bradj.questown.town.interfaces.TownInterface;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.apache.commons.lang3.function.TriFunction;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.function.BiFunction;

public class JobsRegistry {

    private static final SnapshotFunc GATHERER_SNAPSHOT_FUNC = (job, status, items) -> new SimpleSnapshot<>(
            GathererJournal.Snapshot.NAME,
            ProductionStatus.from(status),
            items
    );

    private interface JobFunc extends BiFunction<TownInterface, UUID, Job<MCHeldItem, ? extends Snapshot<?>, ? extends IStatus<?>>> {}
    private interface SnapshotFunc extends TriFunction<String, String, ImmutableList<MCHeldItem>, Snapshot<MCHeldItem>> {

    }

    private record Funcs (
           JobFunc jobFunc,
           SnapshotFunc snapshotFunc
    ) {
    }

    private static final ImmutableMap<String, Funcs> funcs = ImmutableMap.of(
            "farmer", new Funcs(
                    (town, uuid) -> new FarmerJob(uuid, 6),
                    (job, status, items) -> new FarmerJournal.Snapshot<>(GathererJournal.Status.from(status), items)
            ),
            "baker", new Funcs(
                    (town, uuid) -> new BakerJob(uuid, 6),
                    (job, status, items) -> new BakerJournal.Snapshot<>(GathererJournal.Status.from(status), items)
            ),
            DSmelterJob.NAME, new Funcs(
                    (town, uuid) -> new DSmelterJob(uuid, 6),
                    (job, status, items) -> new SimpleSnapshot<>(DSmelterJob.NAME, ProductionStatus.from(status), items)
            ),
            GathererJournal.Snapshot.NAME, new Funcs(
                    (town, uuid) -> new GathererJob(town, 6, uuid),
                    GATHERER_SNAPSHOT_FUNC
            ),
            "blacksmith", new Funcs(
                    (town, uuid) -> new BlacksmithWoodenPickaxeJob(uuid, 6), // TODO: Add support for smaller inventories
                    (job, status, items) -> new SimpleSnapshot<>("blacksmith", ProductionStatus.from(status), items)
            )
    );

    public static Job<MCHeldItem, ? extends Snapshot<?>, ? extends IStatus<?>> getInitializedJob(
            TownInterface town,
            String jobName,
            @Nullable Snapshot<MCHeldItem> journal,
            UUID ownerUUID
    ) {
        Job<MCHeldItem, ? extends Snapshot<?>, ? extends IStatus<?>> j;
        Funcs fn = funcs.get(jobName);
        if (fn == null) {
            QT.JOB_LOGGER.error("Unknown job name {}. Falling back to gatherer.", jobName);
            j = new GathererJob(town, 6, ownerUUID);
        } else {
            j = fn.jobFunc.apply(town, ownerUUID);
        }
        if (journal != null) {
            j.initialize(journal);
        }
        return j;
    }

    public static Snapshot<MCHeldItem> getNewJournal(
            String job,
            String status,
            ImmutableList<MCHeldItem> heldItems
    ) {
        Funcs f = funcs.get(job);
        if (f == null) {
            QT.JOB_LOGGER.error("No journal snapshot factory for {}. Falling back to Simple/Gatherer", job);
            return GATHERER_SNAPSHOT_FUNC.apply(job, status, heldItems);
        }
        return f.snapshotFunc.apply(job, status, heldItems);
    }
}

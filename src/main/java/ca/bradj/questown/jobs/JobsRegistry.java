package ca.bradj.questown.jobs;

import ca.bradj.questown.QT;
import ca.bradj.questown.blocks.BlacksmithsTableBlock;
import ca.bradj.questown.blocks.OreProcessingBlock;
import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.jobs.blacksmith.BlacksmithWoodenPickaxeJob;
import ca.bradj.questown.jobs.crafter.CrafterBowlJob;
import ca.bradj.questown.jobs.crafter.CrafterSeekingJob;
import ca.bradj.questown.jobs.production.ProductionStatus;
import ca.bradj.questown.jobs.smelter.DSmelterJob;
import ca.bradj.questown.mobs.visitor.GathererJob;
import ca.bradj.questown.town.interfaces.TownInterface;
import ca.bradj.questown.town.special.SpecialQuests;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CraftingTableBlock;
import net.minecraft.world.level.block.SignBlock;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Function;

public class JobsRegistry {

    private static final SnapshotFunc GATHERER_SNAPSHOT_FUNC = (status, items) -> new SimpleSnapshot<>(
            GathererJournal.Snapshot.NAME,
            ProductionStatus.from(status),
            items
    );

    public static boolean isJobBlock(Block b) {
        return funcs.values().stream().anyMatch(v -> v.blockCheckFunc.apply(b));
    }

    public static Collection<String> getAllJobs() {
        return funcs.keySet().stream()
                .filter(v -> !v.equals(GathererJournal.Snapshot.NAME))
                .filter(v -> !v.contains("_seeking"))
                .toList();
    }

    public static ResourceLocation getRoomForJob(String job) {
        return funcs.get(job).baseRoom;
    }

    private interface JobFunc extends BiFunction<TownInterface, UUID, Job<MCHeldItem, ? extends Snapshot<MCHeldItem>, ? extends IStatus<?>>> {}
    private interface SnapshotFunc extends BiFunction<String, ImmutableList<MCHeldItem>, Snapshot<MCHeldItem>> {}
    private interface BlockCheckFunc extends Function<Block, Boolean> {}

    private record Funcs (
           JobFunc jobFunc,
           SnapshotFunc snapshotFunc,
           BlockCheckFunc blockCheckFunc,
           ResourceLocation baseRoom,
           IStatus<?> initialStatus

    ) {
    }

    private static final ResourceLocation NOT_REQUIRED_BECAUSE_META_JOB = null;
    private static final BlockCheckFunc NOT_A_DECLARATIVE_JOB = (block) -> false;
    private static final ImmutableMap<String, Funcs> funcs = ImmutableMap.of(
            "farmer", new Funcs(
                    (town, uuid) -> new FarmerJob(uuid, 6),
                    (status, items) -> new FarmerJournal.Snapshot<>(GathererJournal.Status.from(status), items),
                    NOT_A_DECLARATIVE_JOB,
                    SpecialQuests.FARM,
                    GathererJournal.Status.IDLE
            ),
            "baker", new Funcs(
                    (town, uuid) -> new BakerJob(uuid, 6),
                    (status, items) -> new BakerJournal.Snapshot<>(GathererJournal.Status.from(status), items),
                    NOT_A_DECLARATIVE_JOB,
                    new ResourceLocation("bakery"),
                    GathererJournal.Status.IDLE
            ),
            DSmelterJob.NAME, new Funcs(
                    (town, uuid) -> new DSmelterJob(uuid, 6),
                    (status, items) -> new SimpleSnapshot<>(DSmelterJob.NAME, ProductionStatus.from(status), items),
                    (block) -> block instanceof OreProcessingBlock,
                    new ResourceLocation("smeltery"),
                    ProductionStatus.FACTORY.idle()
            ),
            GathererJournal.Snapshot.NAME, new Funcs(
                    (town, uuid) -> new GathererJob(town, 6, uuid),
                    GATHERER_SNAPSHOT_FUNC,
                    NOT_A_DECLARATIVE_JOB,
                    NOT_REQUIRED_BECAUSE_META_JOB,
                    GathererJournal.Status.IDLE
            ),
            "blacksmith", new Funcs(
                    (town, uuid) -> new BlacksmithWoodenPickaxeJob(uuid, 6), // TODO: Add support for smaller inventories
                    (status, items) -> new SimpleSnapshot<>("blacksmith", ProductionStatus.from(status), items),
                    (block) -> block instanceof BlacksmithsTableBlock,
                    new ResourceLocation("smithy"),
                    ProductionStatus.FACTORY.idle()
            ),
            "crafter", new Funcs(
                    (town, uuid) -> new CrafterBowlJob(uuid, 6), // TODO: Add support for smaller inventories
                    (status, items) -> new SimpleSnapshot<>("crafter", ProductionStatus.from(status), items),
                    (block) -> block instanceof CraftingTableBlock,
                    new ResourceLocation("crafting_room"),
                    ProductionStatus.FACTORY.idle()
            ),
            "crafter_seeking", new Funcs(
                    (town, uuid) -> new CrafterSeekingJob(uuid, 6, id -> town.changeJobForVisitor(uuid, id)),
                    (status, items) -> new SimpleSnapshot<>("crafter_seeking", ProductionStatus.from(status), items),
                    (block) -> block instanceof SignBlock, // TODO: Custom block?
                    NOT_REQUIRED_BECAUSE_META_JOB,
                    ProductionStatus.FACTORY.idle()
            )
    );

    public static Job<MCHeldItem, ? extends Snapshot<?>, ? extends IStatus<?>> getInitializedJob(
            TownInterface town,
            String jobName,
            @NotNull Snapshot<MCHeldItem> journal,
            UUID ownerUUID
    ) {
        return getInitializedJob(town, jobName, journal, null, ownerUUID);
    }

    public static Job<MCHeldItem, ? extends Snapshot<MCHeldItem>, ? extends IStatus<?>> getInitializedJob(
            TownInterface town,
            String jobName,
            ImmutableList<MCHeldItem> heldItems,
            UUID ownerUUID
    ) {
        return getInitializedJob(town, jobName, null, heldItems, ownerUUID);
    }

    private static Job<MCHeldItem, ? extends Snapshot<MCHeldItem>, ? extends IStatus<?>> getInitializedJob(
            TownInterface town,
            String jobName,
            @Nullable Snapshot<MCHeldItem> journal,
            @Nullable ImmutableList<MCHeldItem> heldItems,
            UUID ownerUUID
    ) {
        Job<MCHeldItem, ? extends Snapshot<MCHeldItem>, ? extends IStatus<?>> j;
        Funcs fn = funcs.get(jobName);
        if (fn == null) {
            QT.JOB_LOGGER.error("Unknown job name {}. Falling back to gatherer.", jobName);
            j = new GathererJob(town, 6, ownerUUID);
        } else {
            j = fn.jobFunc.apply(town, ownerUUID);
            if (journal == null && heldItems != null) {
                journal = fn.snapshotFunc.apply(fn.initialStatus.name(), heldItems);
            } else if (heldItems == null) {
                QT.JOB_LOGGER.error("Null items and journal. Probably just lost items.");
            }
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
            return GATHERER_SNAPSHOT_FUNC.apply(status, heldItems);
        }
        return f.snapshotFunc.apply(status, heldItems);
    }
}

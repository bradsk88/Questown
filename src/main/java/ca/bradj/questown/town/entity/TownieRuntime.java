package ca.bradj.questown.town.entity;

import ca.bradj.questown.InventoryFullStrategy;
import ca.bradj.questown.QT;
import ca.bradj.questown.core.Config;
import ca.bradj.questown.core.UtilClean;
import ca.bradj.questown.core.VillagerUUID;
import ca.bradj.questown.core.advancements.VisitorTrigger;
import ca.bradj.questown.core.init.AdvancementsInit;
import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.jobs.*;
import ca.bradj.questown.jobs.declarative.DeclarativeJob;
import ca.bradj.questown.jobs.declarative.nomc.WorkSeekerJob;
import ca.bradj.questown.jobs.gatherer.GathererUnmappedNoToolWorkQtrDay;
import ca.bradj.questown.jobs.production.ProductionStatus;
import ca.bradj.questown.mc.Compat;
import ca.bradj.questown.mobs.visitor.TownieNeed;
import ca.bradj.questown.town.interfaces.TownieBody;
import com.google.common.collect.ImmutableList;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * A townie's live state and per-tick decisions: its job (status, carried items, slot locks), the
 * work it may need to undo, how long it has gone without a supply target, and who is listening to
 * it. Held by the town flag, keyed by the townie's UUID; the townie entity is only its
 * {@link TownieBody}. See {@code town/entity/COMPLEX.md} "Townie runtime and body".
 */
public final class TownieRuntime {

    private final TownFlagBlockEntity town;
    private final UUID uuid;
    private Job<MCHeldItem, ? extends ImmutableSnapshot<MCHeldItem, ?>, ? extends IStatus<?>> job;
    private @Nullable WorkToUndo workToUndo;
    private int ticksWithoutJobTarget;
    private int freezeTicks;
    private @Nullable TownieBody body;
    /** Where the townie was when it last had a body in the world (its body's position otherwise). */
    private Vec3 lastPosition;
    private final List<Consumer<ImmutableList<Ingredient>>> ingrListeners = new ArrayList<>();
    private final List<Runnable> changeListeners = new ArrayList<>();
    private final Collection<Function<Void, Void>> cleanupJobListeners = new ArrayList<>();

    TownieRuntime(
            TownFlagBlockEntity town,
            UUID uuid,
            Job<MCHeldItem, ? extends ImmutableSnapshot<MCHeldItem, ?>, ? extends IStatus<?>> initializedJob,
            @Nullable TownieBody body,
            Vec3 position
    ) {
        this.town = town;
        this.uuid = uuid;
        this.body = body;
        this.lastPosition = position;
        this.changeListeners.add(this::notifyWantedIngredients);
        setJob(initializedJob);
    }

    public UUID getUUID() {
        return uuid;
    }

    @SuppressWarnings("removal")
    public @Nullable VillagerUUID getVUID() {
        return VillagerUUID.from(uuid);
    }

    public TownFlagBlockEntity getTown() {
        return town;
    }

    public @Nullable TownieBody getBody() {
        return body;
    }

    /** Whether the townie's body is in the world (loaded and not removed). */
    public boolean hasBody() {
        return body != null && !body.isRemoved();
    }

    /**
     * A body stands in for this townie again (its chunk reloaded). The runtime keeps everything it
     * had while the townie was unloaded; the body only needs to be shown the job.
     */
    void attachBody(TownieBody b) {
        this.body = b;
        b.showJobName(job.getJobName().translationKey());
    }

    /** The body left the world (its chunk unloaded): the townie stays, where the body last stood. */
    public void detachBody(TownieBody b) {
        if (body != b) {
            return;
        }
        this.lastPosition = b.position();
        this.body = null;
    }

    /** Where the townie is: its body's position, or where it last stood while it has no body. */
    public Vec3 position() {
        if (body == null) {
            return lastPosition;
        }
        return body.position();
    }

    /**
     * Where the townie stands: its body's position. A runtime only does work while it has a body
     * (the realtime path); without one, warp moves the townie.
     */
    public BlockPos blockPosition() {
        if (body == null) {
            throw new IllegalStateException("Townie " + uuid + " has no body to stand anywhere");
        }
        return body.blockPosition();
    }

    public Job<MCHeldItem, ? extends ImmutableSnapshot<MCHeldItem, ?>, ? extends IStatus<?>> getJob() {
        return job;
    }

    /**
     * Only the town flag should change a townie's job. Everyone else changes villager jobs using
     * {@link ca.bradj.questown.town.interfaces.TownInterface#changeJobForVisitorFromBoard} instead.
     */
    void setJob(Job<MCHeldItem, ? extends ImmutableSnapshot<MCHeldItem, ?>, ? extends IStatus<?>> initializedJob) {
        ServerLevel sl = town.getServerLevel();
        if (sl == null) {
            QT.VILLAGER_LOGGER.error("setJob needs the town's server level");
            return;
        }

        Job<MCHeldItem, ? extends ImmutableSnapshot<MCHeldItem, ?>, ? extends IStatus<?>> oldJob = job;
        String oldJobName = oldJob == null ? "null" : oldJob.getId().toNiceString();
        job = initializedJob;
        if (body != null) {
            body.showJobName(initializedJob.getJobName().translationKey());
        }
        QT.VILLAGER_LOGGER.info("Job changed to {} for {} [from {}]", initializedJob.getId(), uuid, oldJobName);
        Collection<? extends Runnable> newCleanup = oldJob == null ? List.of() : oldJob.notifyListenersOfNewJob(l -> {
            Function<Void, Void> out = initializedJob.addStatusListener(l);
            return () -> out.apply(null);
        });

        cleanupJobListeners.forEach(v -> v.apply(null));
        cleanupJobListeners.clear();
        cleanupJobListeners.addAll(newCleanup.stream().map(UtilClean::voidVoid).toList());

        cleanupJobListeners.add(initializedJob.addStatusListener(statusChangeNotifier()));
        cleanupJobListeners.add(initializedJob.addItemInsertionListener(
                (bp, item) -> workToUndo = WorkToUndo.and(workToUndo, initializedJob.getId(), bp, item)
        ));
        cleanupJobListeners.add(initializedJob.addJobCompletionListener(id -> workToUndo = null));
        cleanupJobListeners.add(initializedJob.addJobCompletionListener(id -> town.getPossibleWork().invalidate()));
        cleanupJobListeners.add(initializedJob.addJobCompletionListener(id -> {
            Job<?, ?, ?> completed = ServerJobsRegistry.getInitializedJob(sl, id, getJournalSnapshot(), uuid);
            int earned = completed.getExperienceEarned();
            // Fires once per realtime job-completion (throttled by the world-interaction
            // interval, guarded by workLeft == 0, state reset after extraction) - not
            // per-tick. Shows whether the completion listener fires and the amount, to
            // split listener-not-firing vs getExperienceEarned==0 vs frozen-latch vs
            // starvation. See the no-experience problem (Option B).
            QT.VILLAGER_LOGGER.info(
                    "[Experience] +{} from {} for {}",
                    earned, completed.getId().toNiceString(), uuid
            );
            town.villagerHandle.delegate.addExperience(uuid, earned);
        }));
    }

    private StatusListener statusChangeNotifier() {
        return new StatusListener() {
            @Override
            public Runnable jobChanged(Function<StatusListener, Runnable> listenToNewJob) {
                // Do nothing, this listener gets removed whenever a job changes
                return () -> {
                };
            }

            @Override
            public void statusChanged(IStatus<?> newStatus) {
                List.copyOf(changeListeners).forEach(Runnable::run);
            }
        };
    }

    private void notifyWantedIngredients() {
        Collection<Ingredient> ing = ServerJobsRegistry.getWantedResourcesProvider(getJobId())
                                                       .apply(Jobs.getHeldItems(job));
        ingrListeners.forEach(l -> l.accept(ImmutableList.copyOf(ing)));
    }

    /**
     * The first of the townie's per-tick decisions, in tick order: {@link #switchToWorkSeekerIfStuck},
     * {@link #tickJob}, {@link #steer}, {@link #showOn}. A townie that has collected supplies without
     * a target for too long goes back to seeking work.
     */
    public void switchToWorkSeekerIfStuck() {
        if (ticksWithoutJobTarget <= Compat.configGet(Config.MAX_TICKS_WITHOUT_SUPPLIES).get()) {
            return;
        }
        JobID seeker = WorkSeekerJob.getIDForRoot(job.getId());
        town.villagerHandle.delegate.changeJobForVillager(uuid, seeker, town.villagerHandle.getTick(), false);
    }

    /**
     * The job's own tick, after restoring an unset status from what the body last showed.
     *
     * @return the job that ticked. A tick can change the townie's job (a pre-state-change rule);
     * the rest of this tick steers and shows by the job that ticked, as the entity always did:
     * the new job has not ticked yet and has no rooms or target to offer.
     */
    public Job<MCHeldItem, ? extends ImmutableSnapshot<MCHeldItem, ?>, ? extends IStatus<?>> tickJob(
            @Nullable String statusShownByBody
    ) {
        Job<MCHeldItem, ? extends ImmutableSnapshot<MCHeldItem, ?>, ? extends IStatus<?>> ticked = job;
        if (ticked.getStatus() == null || ticked.getStatus().isUnset()) {
            ticked.initializeStatusFromEntityData(statusShownByBody);
        }
        ticked.tick(town, this);
        return ticked;
    }

    /**
     * Points the body at the ticked job's target, or stops it when that job wants it standing
     * still. The target is (re)applied only when the body has no walk target or is already walking
     * to it: a body walking somewhere else keeps going there (behaviour carried over from the entity).
     */
    public void steer(TownieBody b, Job<MCHeldItem, ? extends ImmutableSnapshot<MCHeldItem, ?>, ? extends IStatus<?>> ticked) {
        if (ticked.shouldStandStill(town, b.blockPosition())) {
            b.clearWalkTarget();
            return;
        }
        Optional<BlockPos> current = b.getWalkTarget();
        @Nullable BlockPos target = ticked.getTarget(b.blockPosition(), b.position(), town);
        if (target == null) {
            return;
        }
        boolean apply = current.map(t -> t.equals(target)).orElse(true);
        if (apply) {
            b.setWalkTarget(target);
        }
    }

    /**
     * Shows the body whether the townie is in town, what it is doing and what it carries (by the
     * job that ticked), and counts how long the townie's current job has been collecting supplies
     * without a target.
     */
    public void showOn(
            TownieBody b,
            ServerLevel sl,
            Job<MCHeldItem, ? extends ImmutableSnapshot<MCHeldItem, ?>, ? extends IStatus<?>> ticked
    ) {
        boolean vis = !ticked.shouldDisappear(town, b.position());
        if (!vis && job.getId().rootId().equals("gatherer")) {
            AdvancementsInit.VISITOR_TRIGGER.triggerForNearestPlayer(
                    sl,
                    VisitorTrigger.Triggers.FirstLeaveToGather,
                    b.blockPosition()
            );
        }
        b.setVisible(vis);
        if (!ticked.isInitialized()) {
            return;
        }
        b.showStatus(ticked.getStatusToSyncToClient());
        syncStorageNeed(b, ticked.getStatus());
        Container inventory = ticked.getInventory();
        b.showMainHandItem(inventory.getItem(0));
        if (!ticked.getGlobalSpecialRules().contains(SpecialRules.ONLY_RENDER_FIRST_ITEM_IN_HAND)) {
            b.showOffhandItem(lastCarriedItem(inventory));
        }
        trackTarget(b);
    }

    /** The item in the highest occupied slot after the first, or empty when only one is carried. */
    private static ItemStack lastCarriedItem(Container inventory) {
        int size = inventory.getContainerSize();
        for (int i = 1; i < size; i++) {
            ItemStack item = inventory.getItem(size - i);
            if (!item.isEmpty()) {
                return item;
            }
        }
        return ItemStack.EMPTY;
    }

    private static void syncStorageNeed(TownieBody b, IStatus<?> jobStatus) {
        TownieNeed need = b.getNeed();
        TownieNeed next = storageNeedAfter(need, jobStatus);
        if (next != need) {
            b.setNeed(next);
        }
    }

    /**
     * NO_STORAGE consumer (2026-10-21 dev request): a townie stopped for lack of storage space
     * bubbles at once. It takes precedence over UNMET_ITEM (full storage is what blocks it right
     * now; TownPossibleWork does not overwrite it) and clears only its own need.
     */
    static TownieNeed storageNeedAfter(TownieNeed need, @Nullable IStatus<?> jobStatus) {
        boolean blocked = ProductionStatus.NO_SPACE.equals(jobStatus);
        if (blocked && (need == TownieNeed.NONE || need == TownieNeed.UNMET_ITEM)) {
            return TownieNeed.NO_STORAGE;
        }
        if (!blocked && need == TownieNeed.NO_STORAGE) {
            return TownieNeed.NONE;
        }
        return need;
    }

    private void trackTarget(TownieBody b) {
        if (!job.isWorking()) {
            return;
        }
        IStatus<?> status = job.getStatus();
        if (status == null) {
            QT.VILLAGER_LOGGER.error("Null status");
        } else if (!status.isCollectingSupplies()) {
            return;
        }
        BlockPos target = job.getTarget(b.blockPosition(), b.position(), town);
        if (target != null) {
            ticksWithoutJobTarget = 0;
        } else {
            ticksWithoutJobTarget++;
        }
    }

    /** A work stroke: the body swings its arm, if the townie has one in the world. */
    public void swingMainHand() {
        if (body != null) {
            body.swingMainHand();
        }
    }

    /** Wears one point of durability off a tool the townie is working with. */
    public void wearTool(ItemStack tool) {
        if (body != null) {
            body.wearTool(tool);
            return;
        }
        ServerLevel sl = town.getServerLevel();
        if (sl != null) {
            tool.hurtAndBreak(1, sl.getRandom(), (LivingEntity) null, () -> {});
        }
    }

    /** The job's next target for the body, or null when the job has none (the body wanders). */
    public @Nullable BlockPos targetFor(TownieBody b) {
        return job.getTarget(b.blockPosition(), b.position(), town);
    }

    public boolean tryGiveItem(
            MCHeldItem v,
            InventoryFullStrategy inventoryFullStrategy
    ) {
        // DEV-DIAG: LOOTTRACE grant-site trace, 2026-10-04 — pin the hunted-meat grant path; grep 'DEV-DIAG' to enumerate; keep/remove per handoff
        QT.VILLAGER_LOGGER.info("[LOOTTRACE] tryGiveItem: item={} strategy={} uuid={}",
                v == null ? "null" : v.get().toString(),
                inventoryFullStrategy,
                UtilClean.truncateMiddle(uuid));
        if (job.addToEmptySlot(v)) {
            return true;
        }
        // TODO: Remember the location of the drop and come back to pick them up
        if (inventoryFullStrategy.equals(InventoryFullStrategy.REMOVE_FROM_WORLD)) {
            return false;
        }
        if (body == null) {
            QT.VILLAGER_LOGGER.error("Townie {} has no body to drop {} at; the item is lost", uuid, v);
            return false;
        }
        body.dropItem(v.get().toMCItemStack());
        return false;
    }

    public @Nullable WorkToUndo getWorkToUndo() {
        return workToUndo;
    }

    public void clearWorkToUndo() {
        this.workToUndo = null;
    }

    public void freeze(int ticks) {
        this.freezeTicks = ticks;
    }

    public boolean isTickFrozen() {
        return freezeTicks > 0;
    }

    /** True, spending one frozen tick, while the townie is frozen. */
    public boolean spendFrozenTick() {
        if (freezeTicks <= 0) {
            return false;
        }
        freezeTicks--;
        return true;
    }

    public void addStatusListener(StatusListener l) {
        cleanupJobListeners.add(job.addStatusListener(l));
    }

    public void removeStatusListener(StatusListener l) {
        job.removeStatusListener(l);
    }

    public void addChangeListener(Runnable l) {
        changeListeners.add(l);
    }

    public void addWantedIngredientsListener(Consumer<ImmutableList<Ingredient>> l) {
        ingrListeners.add(l);
    }

    public void removeWantedIngredientsListener(Consumer<ImmutableList<Ingredient>> l) {
        ingrListeners.remove(l);
    }

    public JobID getJobId() {
        return job.getId();
    }

    public JobName getJobName() {
        return job.getJobName();
    }

    public IStatus<?> getStatus() {
        return job.getStatus();
    }

    public Container getInventory() {
        return job.getInventory();
    }

    public ImmutableSnapshot<MCHeldItem, ?> getJournalSnapshot() {
        return job.getJournalSnapshot();
    }

    public List<Boolean> getSlotLocks() {
        return job.getSlotLockStatuses();
    }

    public DataSlot getLockSlot(int i) {
        return job.getLockSlot(i);
    }

    public BlockPos getLook() {
        return job.getLook();
    }

    public Collection<String> getGlobalSpecialRules() {
        return job.getGlobalSpecialRules();
    }

    public boolean shouldBeNoClip(BlockPos at) {
        return job.shouldBeNoClip(town, at);
    }

    public boolean isJumpingAllowed(BlockState onBlock) {
        return job.isJumpingAllowed(onBlock);
    }

    public boolean shouldStandStill(BlockPos at) {
        return job.shouldStandStill(town, at);
    }

    public boolean canStopWorkingAtAnyTime() {
        return job.canStopWorkingAtAnyTime();
    }

    /**
     * Whether the current job lets the hungry->dining short-circuit snap this townie straight
     * to a dining work.
     */
    public boolean canInstantlySwitchToEating() {
        return job.canInstantlySwitchToEating();
    }

    public boolean canAcceptJob() {
        return GathererUnmappedNoToolWorkQtrDay.ID.rootId().equals(job.getId().rootId());
    }

    /**
     * The job's current work-site progress (processing state, work/time left), for the inspect
     * command's eyes-level readout. Null when the job is not a declarative job or has no current
     * work spot.
     */
    public @Nullable String getWorkProgressDebug() {
        if (job instanceof DeclarativeJob dj) {
            return dj.getWorkProgressDebug(town);
        }
        return null;
    }

    /**
     * The timed leaver trip this townie is currently on (absolute server return tick + total
     * ticks), or null when the townie is not away on a timed trip. Delegates to the declarative
     * job that owns the timer.
     */
    public @Nullable LeaverReturn getLeaverReturn() {
        if (job instanceof DeclarativeJob dj) {
            return dj.getLeaverReturn(town);
        }
        return null;
    }

    @Override
    public String toString() {
        return "TownieRuntime[" + UtilClean.truncateMiddle(uuid) + " " + job.getId().toNiceString() + "]";
    }
}

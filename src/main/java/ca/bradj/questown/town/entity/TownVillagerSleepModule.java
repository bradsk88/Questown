package ca.bradj.questown.town.entity;

import ca.bradj.questown.mobs.visitor.VisitorMobEntity;
import ca.bradj.questown.town.TownVillagerBedsHandle;
import ca.bradj.questown.town.UnsafeTown;
import ca.bradj.questown.town.rooms.TownPosition;
import com.google.common.collect.ImmutableList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;

public class TownVillagerSleepModule implements VillagerSleepModule<VisitorMobEntity> {
    private final UnsafeTown town = new UnsafeTown(getClass());
    private final TownVillagerBedsHandle beds = new TownVillagerBedsHandle();

    /**
     * Claims that arrived before the town initialized. Entity load out-runs the flag's
     * initializer: a townie deserialized before {@link #associate} must not be
     * dropped (vanilla aborts the load when {@code claimBed} throws), so its bed
     * claim is deferred to this point.
     */
    private final List<VisitorMobEntity> pendingClaims = new ArrayList<>();

    public void associate(TownFlagBlockEntity t) {
        town.initialize(t);
        List<VisitorMobEntity> pending = new ArrayList<>(pendingClaims);
        pendingClaims.clear();
        pending.forEach(this::claimBed);
    }

    @Override
    public void tick(List<VisitorMobEntity> entities) {
        TownFlagBlockEntity t = town.getUnsafe();
        beds.tick(t, ImmutableList.copyOf(entities));
        entities.forEach(e -> {
            Optional<GlobalPos> bestBed = beds.getBestBed(t, e);
            e.getBrain().setMemory(MemoryModuleType.HOME, bestBed);
        });
    }

    @Override
    public void addSleepListener(VisitorMobEntity e, BiConsumer<TownPosition, Long> listener) {
        e.addSleepListener(event -> {
            BlockPos p = event.bedPos();
            listener.accept(new TownPosition(p.getX(), p.getY(), p.getZ()), event.duration());
        });
    }

    @Override
    public void claimBed(VisitorMobEntity vEntity) {
        if (!town.isInitialized()) {
            // Load-time registration: the town's bed map is not resolvable yet.
            // Deferring instead of throwing keeps the loading entity alive;
            // associate() drains the queue once the flag initializes.
            pendingClaims.add(vEntity);
            return;
        }
        beds.claim(vEntity, town.getUnsafe());
    }

    @Override
    public void stopSleeping(VisitorMobEntity visitorMobEntity) {
        visitorMobEntity.stopSleeping();
    }

    @Override
    public boolean isSleeping(VisitorMobEntity e) {
        return e.isSleeping();
    }

    @Override
    public boolean hasBed(VisitorMobEntity e) {
        return beds.getBestBed(town.getUnsafe(), e).isPresent();
    }

    @Override
    public boolean bedsKnown() {
        // Beds come from recipe-matched rooms, which the room scan rebuilds after a load.
        return town.isInitialized() && town.getUnsafe().getRoomHandle().getRegisteredRooms().hasScannedSinceLoad();
    }
}

package ca.bradj.questown.town.entity;

import ca.bradj.questown.mobs.visitor.VisitorMobEntity;
import ca.bradj.questown.town.TownVillagerBedsHandle;
import ca.bradj.questown.town.UnsafeTown;
import ca.bradj.questown.town.rooms.TownPosition;
import com.google.common.collect.ImmutableList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;

import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;

public class TownVillagerSleepModule implements VillagerSleepModule<VisitorMobEntity> {
    private final UnsafeTown town = new UnsafeTown(getClass());
    private final TownVillagerBedsHandle beds = new TownVillagerBedsHandle();

    public void associate(TownFlagBlockEntity t) {
        town.initialize(t);
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
}

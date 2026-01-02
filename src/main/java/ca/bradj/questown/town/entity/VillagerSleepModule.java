package ca.bradj.questown.town.entity;

import ca.bradj.questown.town.rooms.TownPosition;

import java.util.List;
import java.util.function.BiConsumer;

public interface VillagerSleepModule<ENTITY> {
    void tick(List<ENTITY> entities);
    void addSleepListener(ENTITY vEntity, BiConsumer<TownPosition, Long> listener);
    void claimBed(ENTITY vEntity);

    void stopSleeping(ENTITY entity);

    boolean isSleeping(ENTITY e);
}

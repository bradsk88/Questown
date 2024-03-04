package ca.bradj.questown.town.interfaces;

import ca.bradj.questown.town.VillagerStatsData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

import java.util.Collection;
import java.util.UUID;
import java.util.function.Consumer;

public interface VillagerHolder {
    void addStatsListener(Consumer<VillagerStatsData> l);

    void removeStatsListener(Consumer<VillagerStatsData> villagerStatsMenu);

    Collection<LivingEntity> entities();

    void remove(LivingEntity entity);

    void showUI(
            ServerPlayer sender,
            String type,
            UUID villagerId
    );

    void fillHunger(UUID uuid);

    void makeAngry(UUID uuid);

    boolean isDining(UUID uuid);

    void applyEffect(ResourceLocation effect, Long expireOnTick, UUID uuid);

    int getAffectedTime(UUID uuid, Integer timeToAugment);

    int getWorkSpeed(UUID uuid);
}

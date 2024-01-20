package ca.bradj.questown.town;

import ca.bradj.questown.QT;
import ca.bradj.questown.core.Config;
import ca.bradj.questown.core.network.OpenVillagerMenuMessage;
import ca.bradj.questown.gui.*;
import ca.bradj.questown.mobs.visitor.VisitorMobEntity;
import ca.bradj.questown.town.interfaces.VillagerHolder;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraftforge.network.NetworkHooks;
import org.apache.commons.lang3.function.TriFunction;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Stream;

public class TownVillagerHandle implements VillagerHolder {

    final List<LivingEntity> entities = new ArrayList<>();
    final Map<UUID, Integer> fullness = new HashMap<>();
    private List<Consumer<VillagerStatsData>> listeners = new ArrayList<>();
    private List<Consumer<VisitorMobEntity>> hungryListeners = new ArrayList<>();

    public void tick() {
        entities.forEach(e -> {
            UUID u = e.getUUID();
            int oldVal = fullness.getOrDefault(u, Config.BASE_FULLNESS.get());
            int newVal = Math.max(0, oldVal - 1);
            fullness.put(u, newVal);
            if (oldVal != newVal) {
                listeners.forEach(l -> l.accept(getStats(u)));
                if (newVal == 0) {
                    hungryListeners.forEach(l -> l.accept((VisitorMobEntity) e));
                }
            }
        });
    }

    public VillagerStatsData getStats(UUID uuid) {
        float fullnessPercent = (float) fullness.get(uuid) / Config.BASE_FULLNESS.get();
        return new VillagerStatsData(
                // TODO: Get max fullness from villager
                fullnessPercent
        );
    }

    public Stream<LivingEntity> stream() {
        return entities.stream();
    }

    public void remove(LivingEntity visitorMobEntity) {
        this.entities.remove(visitorMobEntity);
    }

    @Override
    public void showUI(
            ServerPlayer sender,
            String type,
            UUID villagerId
    ) {
        Optional<LivingEntity> f = stream()
                .filter(VisitorMobEntity.class::isInstance)
                .filter(v -> villagerId.equals(v.getUUID()))
                .findFirst();
        if (f.isEmpty()) {
            QT.FLAG_LOGGER.error("No villagers with ID {} while opening UI", villagerId);
            return;
        }

        VisitorMobEntity e = (VisitorMobEntity) f.get();

        List<UIQuest> quests = ImmutableList.of(); // TODO: Load quests

        ImmutableMap<String, TriFunction<Integer, Inventory, Player, AbstractContainerMenu>> showers = ImmutableMap.of(
                OpenVillagerMenuMessage.INVENTORY, (windowId, inv, p) -> new InventoryAndStatusMenu(
                        windowId, e.getInventory(), p.getInventory(), e.getSlotLocks(), e, e.getJobId(), e.getFlagPos()
                ),
                OpenVillagerMenuMessage.QUESTS,(windowId, inv, p) -> new TownQuestsContainer(
                        windowId, quests, e.getFlagPos()
                ),
                OpenVillagerMenuMessage.STATS, (windowId, inv, p) -> new VillagerStatsMenu(
                        windowId, e, e.getFlagPos()
                )
        );

        NetworkHooks.openScreen(sender, new MenuProvider() {
            @Override
            public @NotNull Component getDisplayName() {
                return Component.empty();
            }

            @Override
            public @NotNull AbstractContainerMenu createMenu(
                    int windowId,
                    @NotNull Inventory inv,
                    @NotNull Player p
            ) {
                return showers.get(type).apply(windowId, inv, p);
            }
        }, data -> VillagerMenus.write(data, quests, e, e.getInventory().getContainerSize(), e.getJobId()));
    }

    @Override
    public void fillHunger(UUID uuid) {
        // TODO: Get max fullness from villager
        fullness.put(uuid, Config.BASE_FULLNESS.get());
    }

    void forEach(Consumer<? super LivingEntity> c) {
        this.entities.forEach(c);
    }

    public boolean isEmpty() {
        return this.entities.isEmpty();
    }

    public long size() {
        return entities.size();
    }

    public void add(VisitorMobEntity vEntity) {
        this.entities.add(vEntity);
    }

    public boolean exists(VisitorMobEntity visitorMobEntity) {
        return entities.contains(visitorMobEntity);
    }

    @Override
    public void addStatsListener(Consumer<VillagerStatsData> l) {
        this.listeners.add(l);
    }

    public void addHungryListener(Consumer<VisitorMobEntity> l) {
        this.hungryListeners.add(l);
    }

    @Override
    public void removeStatsListener(Consumer<VillagerStatsData> l) {
        this.listeners.remove(l);
    }

    @Override
    public Collection<LivingEntity> entities() {
        return this.entities;
    }
}

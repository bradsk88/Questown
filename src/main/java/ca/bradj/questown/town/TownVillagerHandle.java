package ca.bradj.questown.town;

import ca.bradj.questown.QT;
import ca.bradj.questown.core.Config;
import ca.bradj.questown.core.network.OpenVillagerMenuMessage;
import ca.bradj.questown.gui.*;
import ca.bradj.questown.items.EffectMetaItem;
import ca.bradj.questown.jobs.JobsRegistry;
import ca.bradj.questown.mobs.visitor.VisitorMobEntity;
import ca.bradj.questown.town.interfaces.TownInterface;
import ca.bradj.questown.town.interfaces.VillagerHolder;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
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

    public static final TownVillagerHandlerSerializer SERIALIZER = new TownVillagerHandlerSerializer();

    final Map<UUID, Integer> fullness = new HashMap<>();
    final TownVillagerMoods moods = new TownVillagerMoods();

    private final List<LivingEntity> entities = new ArrayList<>();
    private final List<Consumer<VillagerStatsData>> listeners = new ArrayList<>();
    private final List<Consumer<VisitorMobEntity>> hungryListeners = new ArrayList<>();

    public void initialize(
            Map<UUID, Integer> fullness,
            Map<UUID, ImmutableList<Effect>> moodEffects
    ) {
        if (!this.fullness.isEmpty()) {
            throw new IllegalStateException("Attempting to initialize already active fullness");
        }
        this.fullness.putAll(fullness);

        this.moods.initialize(moodEffects);
    }

    public void tick(long currentTick) {
        tickHunger();
        moods.tick(currentTick);
    }

    private void tickHunger() {
        entities.forEach(e -> {
            UUID u = e.getUUID();
            int oldVal = fullness.getOrDefault(u, Config.BASE_FULLNESS.get());
            int newVal = Math.max(0, oldVal - 10);
            fullness.put(u, newVal);
            if (newVal != oldVal) {
                listeners.forEach(l -> l.accept(getStats(u)));
            }
            if (newVal == 0) {
                hungryListeners.forEach(l -> l.accept((VisitorMobEntity) e));
            }
        });
    }

    public VillagerStatsData getStats(UUID uuid) {
        float fullnessPercent = (float) fullness.get(uuid) / Config.BASE_FULLNESS.get();
        return new VillagerStatsData(
                // TODO: Get max fullness from villager
                fullnessPercent,
                moods.getMood(uuid)
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

        TownInterface flag = (TownFlagBlockEntity) sender.getLevel().getBlockEntity(e.getFlagPos());

        List<UIQuest> quests = flag.getQuestHandle().getAllBatchesForVillager(e.getUUID()).stream().map(
                v -> UIQuest.fromLevel(sender.getLevel(), v)
        ).flatMap(List::stream).toList();

        VillagerStatsData stats = flag.getVillagerHandle().getStats(e.getUUID());

        ImmutableMap<String, TriFunction<Integer, Inventory, Player, AbstractContainerMenu>> showers = ImmutableMap.of(
                OpenVillagerMenuMessage.INVENTORY, (windowId, inv, p) -> new InventoryAndStatusMenu(
                        windowId, e.getInventory(), p.getInventory(), e.getSlotLocks(), e, e.getJobId(), e.getFlagPos()
                ),
                OpenVillagerMenuMessage.QUESTS, (windowId, inv, p) -> new VillagerQuestsContainer(
                        windowId, e.getUUID(), quests, e.getFlagPos()
                ),
                OpenVillagerMenuMessage.STATS, (windowId, inv, p) -> new VillagerStatsMenu(
                        windowId, e, e.getFlagPos(), stats
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
        }, data -> VillagerMenus.write(data, quests, e, e.getInventory().getContainerSize(), e.getJobId(), stats));
    }

    @Override
    public void fillHunger(UUID uuid) {
        // TODO: Get max fullness from villager
        fullness.put(uuid, Config.BASE_FULLNESS.get());
    }

    @Override
    public void makeAngry(UUID uuid) {
        // TODO: Implement happiness (happy = 100% work speed angry = 50% work speed)
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

    public void makeAllTotallyHungry() {
        entities.forEach(e -> {
            UUID u = e.getUUID();
            fullness.put(u, 1);
            // Listeners will be notified on next tick
        });
    }

    @Override
    public boolean isDining(UUID uuid) {
        return entities.stream()
                .filter(v -> uuid.equals(v.getUUID()))
                .map(v -> JobsRegistry.isDining(((VisitorMobEntity) v).getJobId()))
                .findFirst()
                .orElse(false);
    }

    @Override
    public void applyEffect(ResourceLocation effect, Long expireOnTick, UUID uuid) {
        // TODO: Generalize
        if (EffectMetaItem.ConsumableEffects.FILL_HUNGER.equals(effect)) {
            fillHunger(uuid);
            return;
        }
        moods.tryApplyEffect(effect, expireOnTick, uuid);
    }

    @Override
    public int getAffectedTime(UUID uuid, Integer timeToAugment) {
        float offset = (Config.NEUTRAL_MOOD.get() - moods.getMood(uuid));
        return (int) ((1f + offset) * timeToAugment);
    }

    @Override
    public int getWorkSpeed(UUID uuid) {
        return (int) (moods.getMood(uuid) * 10);
    }
}

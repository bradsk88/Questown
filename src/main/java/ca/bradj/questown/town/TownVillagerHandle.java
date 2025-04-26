package ca.bradj.questown.town;

import ca.bradj.questown.QT;
import ca.bradj.questown.core.Config;
import ca.bradj.questown.core.UtilClean;
import ca.bradj.questown.core.advancements.RoomTrigger;
import ca.bradj.questown.core.init.AdvancementsInit;
import ca.bradj.questown.core.network.*;
import ca.bradj.questown.gui.*;
import ca.bradj.questown.items.EffectMetaItem;
import ca.bradj.questown.jobs.*;
import ca.bradj.questown.mc.Compat;
import ca.bradj.questown.mc.Util;
import ca.bradj.questown.mobs.visitor.VisitorMobEntity;
import ca.bradj.questown.town.interfaces.TownInterface;
import ca.bradj.questown.town.interfaces.VillagerHolder;
import ca.bradj.questown.town.special.SpecialQuests;
import ca.bradj.roomrecipes.recipes.RecipesInit;
import ca.bradj.roomrecipes.recipes.RoomRecipe;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.network.PacketDistributor;
import org.apache.commons.lang3.function.TriFunction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class TownVillagerHandle implements VillagerHolder {

    public static final TownVillagerHandlerSerializer SERIALIZER = new TownVillagerHandlerSerializer();

    final Map<UUID, Integer> fullness = new HashMap<>();
    final Map<UUID, Integer> experience = new HashMap<>();
    final Map<UUID, Integer> levels = new HashMap<>();
    final Map<UUID, Integer> damage = new HashMap<>();
    final Map<UUID, PoseInPlace> requestedPose = new HashMap<>();
    final Map<UUID, Collection<JobID>> unlockedJobs = new HashMap<>();
    final TownVillagerMoods moods = new TownVillagerMoods();

    private final List<LivingEntity> entities = new ArrayList<>();
    private final List<Consumer<VillagerStatsData>> listeners = new ArrayList<>();
    private final List<Consumer<VisitorMobEntity>> hungryListeners = new ArrayList<>();
    private final UnsafeTown town = new UnsafeTown(getClass());

    private static final int TICK_FACTOR = 10;
    private final TownVillagerBedsHandle beds = new TownVillagerBedsHandle();

    public void initialize(
            Map<UUID, Integer> fullness,
            Map<UUID, ? extends ImmutableCollection<Effect>> moodEffects,
            Map<UUID, Integer> damage,
            Map<UUID, ? extends ImmutableCollection<JobID>> unlockedJobs
    ) {
        if (!this.fullness.isEmpty()) {
            throw new IllegalStateException("Attempting to initialize already initialized");
        }
        this.fullness.putAll(fullness);
        this.moods.initialize(moodEffects);
        this.damage.putAll(damage);
        this.unlockedJobs.putAll(unlockedJobs);
    }

    public void tick(
            long currentTick,
            Signals signals
    ) {
        if (signals != Signals.NIGHT) {
            tickHunger();
        }
        tickDamage();
        moods.tick(currentTick);
        TownFlagBlockEntity t = town.getUnsafe();
        beds.tick(t, ImmutableList.copyOf(entities));
        entities.forEach(e -> {
            Optional<GlobalPos> bestBed = beds.getBestBed(t, e);
            e.getBrain().setMemory(MemoryModuleType.HOME, bestBed);
        });
    }

    private void tickHunger() {
        if (!Config.HUNGER_ENABLED.get()) {
            entities.forEach(e -> {
                fullness.put(e.getUUID(), Config.BASE_FULLNESS.get());
            });
            return;
        }

        Map<UUID, Integer> map = fullness;
        Integer base = Config.BASE_FULLNESS.get();
        BiConsumer<Integer, LivingEntity> then = (newVal, e) -> {
            if (newVal == 0) {
                hungryListeners.forEach(l -> l.accept((VisitorMobEntity) e));
            }
        };
        tickThing(map, base, e -> 10, then);
    }

    private void tickDamage() {
        tickThing(
                damage, 0, e -> applyHealFactor(e, 100), (newVal, e) -> {
                }
        );
    }

    private int applyHealFactor(
            LivingEntity e,
            int i
    ) {
        if (!(e instanceof VisitorMobEntity)) {
            return i;
        }
        PoseInPlace pose = requestedPose.get(e.getUUID());
        if (pose == null) {
            return i;
        }
        if (!Pose.SLEEPING.equals(pose.pose())) {
            return i;
        }
        @NotNull TownFlagBlockEntity t = town.getUnsafe();
        Double bedFactor = Config.NORMAL_BED_HEAL_MULTIPLIER.get();
        Double boostedFactor = t.getHealingHandle().getHealFactor(e.blockPosition());
        Double hf = Math.min(bedFactor, boostedFactor);
        int i1 = (int) (i * hf);
        QT.VILLAGER_LOGGER.debug("Healing by {} due to sleeping heal factor {} {}", i1, hf, e.getUUID());
        return i1;
    }

    private void tickThing(
            Map<UUID, Integer> map,
            Integer base,
            Function<LivingEntity, Integer> amount,
            BiConsumer<Integer, LivingEntity> then
    ) {
        entities.forEach(e -> {
            UUID u = e.getUUID();
            int oldVal = map.getOrDefault(u, base);
            int newVal = Math.max(0, oldVal - amount.apply(e));
            map.put(u, newVal);
            if (newVal != oldVal) {
                listeners.forEach(l -> l.accept(getStats(u)));
            }
            then.accept(newVal, e);
        });
    }

    public VillagerStatsData getStats(UUID uuid) {
        Integer bf = Config.BASE_FULLNESS.get();
        float fullnessPercent = (float) Util.getOrDefault(fullness, uuid, bf) / bf;
        float damagePercent = getDamagePercent(uuid);
        Integer experiencePercent = Util.getOrDefault(experience, uuid, 0);
        return new VillagerStatsData(
                // TODO: Track max fullness per villager based on their traits
                fullnessPercent, experiencePercent, moods.getMood(uuid), damagePercent);
    }

    public float getDamagePercent(UUID uuid) {
        return (float) Util.getOrDefault(damage, uuid, 0) / (16 * Config.DAMAGE_TICKS.get() * TICK_FACTOR);
    }

    @Override
    public Collection<JobID> getJobs() {
        return entities.stream().map(v -> ((VisitorMobEntity) v).getJobId()).toList();
    }

    @Override

    public void changeJobForVillager(
            UUID visitorUUID,
            JobID jobID,
            boolean announce
    ) {
        @NotNull TownFlagBlockEntity t = town.getUnsafe();
        VisitorMobEntity f = getEntity(visitorUUID);
        if (f == null) {
            QT.FLAG_LOGGER.error("Could not find entity {} to apply job change: {}", visitorUUID, jobID);
        } else {
            doSetJob(visitorUUID, jobID, f);
            t.setChanged();
            if (announce) {
                t.messages.jobChanged(jobID, visitorUUID);
            }
        }

        t.possibleWork.invalidate();
    }

    @SuppressWarnings("deprecation")
    private void doSetJob(
            UUID visitorUUID,
            JobID jobName,
            VisitorMobEntity f
    ) {
        f.setJob(ServerJobsRegistry.getInitializedJob(
                town.getServerLevelUnsafe(),
                jobName,
                f.getJobJournalSnapshot().items(),
                visitorUUID
        ));
    }

    @Override
    public void changeToNextJobForVillager(
            UUID villagerUUID,
            JobID currentJob
    ) {
        this.town.getUnsafe().changeJobForVisitorFromBoard(villagerUUID, currentJob);
    }

    public Stream<LivingEntity> stream() {
        return entities.stream();
    }

    public void remove(LivingEntity visitorMobEntity) {
        this.entities.remove(visitorMobEntity);
        town.getUnsafe().setChanged();
    }

    @Override
    public void showUI(
            ServerPlayer sender,
            String type,
            UUID villagerId
    ) {
        Optional<LivingEntity> f = stream().filter(VisitorMobEntity.class::isInstance)
                                           .filter(v -> villagerId.equals(v.getUUID())).findFirst();
        if (f.isEmpty()) {
            QT.FLAG_LOGGER.error("No villagers with ID {} while opening UI", villagerId);
            return;
        }

        syncWorkToClient(sender);

        VisitorMobEntity e = (VisitorMobEntity) f.get();

        TownInterface flag = (TownFlagBlockEntity) sender.getLevel().getBlockEntity(e.getFlagPos());

        List<UIQuest> quests = flag.getQuestHandle().getAllBatchesForVillager(e.getUUID()).stream()
                                   .map(v -> UIQuest.fromLevel(sender.getLevel(), v)).flatMap(List::stream).toList();

        VillagerStatsData stats = flag.getVillagerHandle().getStats(e.getUUID());
        VillagerEconomicsData econ = new VillagerEconomicsData(ImmutableList.of());

        ImmutableMap<String, Runnable> showers = ImmutableMap.of(
                OpenVillagerMenuMessage.INVENTORY, () -> openMenu(
                        sender, (windowId, inv, p) -> {
                            InventoryAndStatusMenu x = new InventoryAndStatusMenu(
                                    windowId,
                                    e.getInventory(),
                                    p.getInventory(),
                                    e.getSlotLocks(),
                                    e.getUUID(),
                                    e.getJobId(),
                                    e.getFlagPos()
                            );
                            x.connectToServer(e, sender);
                            return x;
                        }, quests, e, stats
                ), OpenVillagerMenuMessage.QUESTS, () -> openMenu(
                        sender,
                        (windowId, inv, p) -> new VillagerQuestsContainer(
                                windowId,
                                e.getUUID(),
                                quests,
                                e.getFlagPos()
                        ),
                        quests,
                        e,
                        stats
                ), OpenVillagerMenuMessage.STATS, () -> openMenu(
                        sender,
                        (windowId, inv, p) -> new VillagerStatsMenu(windowId, e, e.getFlagPos(), stats),
                        quests,
                        e,
                        stats
                ), OpenVillagerMenuMessage.SKILLS, () -> {
                    Collection<JobID> vUnlocked = UtilClean.getOrDefaultCollection(
                            unlockedJobs,
                            e.getUUID(),
                            ImmutableList.of()
                    );
                    OpenVillagerAdvancementsMenuMessage msg = new OpenVillagerAdvancementsMenuMessage(
                            e.getFlagPos(),
                            e.getUUID(),
                            vUnlocked,
                            e.getJobId()
                    );
                    QuestownNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sender), msg);
                }, OpenVillagerMenuMessage.ECONOMICS, () -> {
                    NoMCEconomics tEcon = flag.getEconomicsHandle();
                    ImmutableList<ItemEconomicsData> aggregated = tEcon.getAggregatedItems(villagerId);
                    QuestownNetwork.CHANNEL.send(
                            PacketDistributor.PLAYER.with(() -> sender),
                            new EconomicsUpdate(aggregated)
                    );
                    openMenu(
                            sender,
                            (windowId, inv, p) -> new VillagerEconomicsMenu(windowId, e, e.getFlagPos(), econ),
                            quests,
                            e,
                            stats
                    );
                }
        );

        Runnable runnable = showers.get(type);
        if (runnable == null) {
            throw new IllegalArgumentException("Unexpected menu type: \"" + type + "\"");
        }
        runnable.run();
    }

    private static void syncWorkToClient(ServerPlayer sender) {
        PacketDistributor.PacketTarget tgt = PacketDistributor.PLAYER.with(() -> sender);
        Map<JobID, @Nullable JobID> b = new HashMap<>();
        Map<JobID, ResourceLocation> b2 = new HashMap<>();
        Works.values().forEach(w -> {
            Work work = w.get();
            b.put(work.id, work.parentID);
            b2.put(work.id, Compat.getItemId(work.icon.getItem()));
        });
        QuestownNetwork.CHANNEL.send(tgt, new SyncVillagerAdvancementsMessage(b, b2));
    }

    private static void openMenu(
            ServerPlayer sender,
            TriFunction<Integer, Inventory, Player, AbstractContainerMenu> shower,
            List<UIQuest> quests,
            VisitorMobEntity e,
            VillagerStatsData stats
    ) {
        Compat.openScreen(
                sender, new MenuProvider() {
                    @Override
                    public @NotNull Component getDisplayName() {
                        return Compat.literal("");
                    }

                    @Override
                    public @NotNull AbstractContainerMenu createMenu(
                            int windowId,
                            @NotNull Inventory inv,
                            @NotNull Player p
                    ) {
                        return shower.apply(windowId, inv, p);
                    }
                }, data -> VillagerMenus.write(
                        data,
                        quests,
                        e,
                        e.getInventory().getContainerSize(),
                        e.getJobId(),
                        stats,
                        new VillagerEconomicsData(ImmutableList.of())
                        // TODO: Actually send econ data
                        // TODO: Finish testing this UI
                )
        );
    }

    @Override
    public void fillHunger(UUID uuid) {
        // TODO: Get max fullness from villager
        fillHunger(uuid, 1.0f);
    }

    @Override
    public void fillHunger(
            UUID uuid,
            float percent
    ) {
        fullness.put(uuid, (int) (percent * Config.BASE_FULLNESS.get()));
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
        for (JobID jobID : ServerJobsRegistry.getDefaultWork(vEntity.getJobId())) {
            unlockJob(vEntity.getUUID(), jobID);
        }
        this.beds.claim(vEntity, town.getUnsafe());
        vEntity.addSleepListener(e -> {
            Double healFactor = town.getUnsafe().getHealingHandle().getHealFactor(e.bedPos());
            long ticksHealed = (long) (e.duration() * healFactor);
            damage.compute(
                    vEntity.getUUID(), (id, cur) -> {
                        if (cur == null) {
                            return 0;
                        }
                        int newVal = Math.toIntExact(Math.max(0, cur - ticksHealed));
                        QT.VILLAGER_LOGGER.debug(
                                "Villager damage changed from {} to {} after {} ticks of sleep via bed at {} with heal factor {} [{}]",
                                cur,
                                newVal,
                                e.duration(),
                                e.bedPos(),
                                healFactor,
                                vEntity.getUUID()
                        );
                        return newVal;
                    }
            );
        });
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
        if (!Config.HUNGER_ENABLED.get()) {
            return;
        }
        entities.forEach(e -> {
            UUID u = e.getUUID();
            fullness.put(u, 1);
            // Listeners will be notified on next tick
        });
    }

    @Override
    public boolean isDining(UUID uuid) {
        return entities.stream().filter(v -> uuid.equals(v.getUUID()))
                       .map(v -> ServerJobsRegistry.isDining(((VisitorMobEntity) v).getJobId())).findFirst()
                       .orElse(false);
    }

    @Override
    public boolean canDine(UUID uuid) {
        return entities.stream().filter(v -> uuid.equals(v.getUUID()))
                       .map(v -> ((VisitorMobEntity) v).canStopWorkingAtAnyTime()).findFirst().orElse(false);
    }

    @Override
    public void applyEffect(
            ResourceLocation effect,
            Long expireOnTick,
            UUID uuid
    ) {
        // TODO: Generalize
        if (EffectMetaItem.ConsumableEffects.FILL_HUNGER.equals(effect)) {
            fillHunger(uuid);
            return;
        }
        if (EffectMetaItem.ConsumableEffects.FILL_HUNGER_HALF.equals(effect)) {
            fillHunger(uuid, 0.5f);
            return;
        }
        moods.tryApplyEffect(effect, expireOnTick, uuid);
    }

    @Override
    public int getAffectedTime(
            UUID uuid,
            Integer timeToAugment
    ) {
        float offset = ((Config.NEUTRAL_MOOD.get() / 100f) - moods.getMood(uuid));
        return (int) ((1f + offset) * timeToAugment);
    }

    @Override
    public int getWorkSpeed(UUID uuid) {
        return (int) (moods.getMood(uuid) * 10);
    }

    /**
     * @deprecated Eventually this handle should not require a reference to the flag entity
     */
    public void associate(TownFlagBlockEntity t) {
        this.town.initialize(t);
    }

    @Override
    public void freezeVillagers(Integer ticks) {
        stream().filter(VisitorMobEntity.class::isInstance).map(VisitorMobEntity.class::cast)
                .forEach(v -> v.freeze(ticks));
    }

    @Override
    public void recallVillagers() {
        final BlockPos visitorJoinPos = town.getUnsafe().getBlockPos();
        forEach(v -> {
            QT.FLAG_LOGGER.debug("Moving {} to {} and hungerUpdater", v, visitorJoinPos);
            v.setPos(visitorJoinPos.getX(), visitorJoinPos.getY(), visitorJoinPos.getZ());
            v.setHealth(v.getMaxHealth());
        });
    }

    @Override
    public void validateEntity(VisitorMobEntity visitorMobEntity) {
        if (exists(visitorMobEntity)) {
            return;
        }
        QT.FLAG_LOGGER.error("Visitor mob's parent has no record of entity. Removing visitor");
        visitorMobEntity.remove(Entity.RemovalReason.DISCARDED);
    }

    public VisitorMobEntity getEntity(UUID ownerUUID) {
        Optional<LivingEntity> f = stream().filter(v -> ownerUUID.equals(v.getUUID())).findFirst();
        if (f.isEmpty()) {
            QT.FLAG_LOGGER.error("No entities found for UUID: {}", ownerUUID);
            return null;
        }
        LivingEntity ff = f.get();
        if (!(ff instanceof VisitorMobEntity v)) {
            QT.FLAG_LOGGER.error("Entity is wrong type: {}", ff);
            return null;
        }
        return v;
    }

    @Override
    public void addDamage(UUID uuid) {
        Integer oldVal = Util.getOrDefault(damage, uuid, 0);
        int addition = (int) (Config.DAMAGE_TICKS.get() * TICK_FACTOR);
        damage.put(uuid, oldVal + addition);
    }

    @Override
    public int getDamageTicksLeft(UUID uuid) {
        return Util.getOrDefault(damage, uuid, 0) / TICK_FACTOR;
    }

    @Override
    public void requestPose(
            UUID ownerUUID,
            PoseInPlace pose
    ) {
        requestedPose.put(ownerUUID, pose);
    }

    @Override
    public Optional<PoseInPlace> getRequestedPose(UUID ownerUUID) {
        return Optional.ofNullable(requestedPose.get(ownerUUID));
    }

    @Override
    public void clearPoseRequests(UUID uuid) {
        requestedPose.remove(uuid);
    }

    @Override
    public void showMultiStatusUI(ServerPlayer player) {
        List<VisitorMobEntity> es = entities.stream().map(v -> (VisitorMobEntity) v).toList();

        BlockPos townFlagBasePos = town.getUnsafe().getTownFlagBasePos();
        Compat.openScreen(
                player, new MenuProvider() {
                    @Override
                    public @NotNull Component getDisplayName() {
                        return Compat.literal("");
                    }

                    @Override
                    public @NotNull AbstractContainerMenu createMenu(
                            int windowId,
                            @NotNull Inventory inv,
                            @NotNull Player p
                    ) {
                        MultiStatusMenu multiStatusMenu = new MultiStatusMenu(
                                windowId,
                                townFlagBasePos,
                                this::triggerAdvancement
                        );
                        return multiStatusMenu;
                    }

                    private void triggerAdvancement() {
                        AdvancementsInit.ROOM_TRIGGER.triggerForNearestPlayer(
                                player.getLevel(),
                                RoomTrigger.Triggers.FirstOpenFlagMenu,
                                townFlagBasePos
                        );
                    }
                }, data -> {
                    List<UIQuest> quests = UIQuest.fromLevel(
                            player.getLevel(),
                            town.getUnsafe().getAllQuestsWithRewards()
                    );
                    FlagMenus.writeAndLink(data, quests, townFlagBasePos, player, es);
                }
        );
    }

    @Override
    public void showItemJobsUI(
            ServerPlayer sender,
            Ingredient itemToShowJobsFor
    ) {
        Map<JobID, List<UUID>> vb = new HashMap<>();
        for (LivingEntity entity : entities) {
            if (!(entity instanceof VisitorMobEntity vme)) {
                continue;
            }
            Util.addOrInitialize(vb, vme.getJobId(), vme.getUUID());
        }

        ImmutableMap.Builder<ResourceLocation, RoomRecipe> rMapB = ImmutableMap.builder();
        SpecialQuests.SPECIAL_QUESTS.forEach(rMapB::put);
        sender.getLevel().getRecipeManager().getAllRecipesFor(RecipesInit.ROOM).forEach(v -> rMapB.put(v.getId(), v));
        ImmutableMap<ResourceLocation, RoomRecipe> rMap = rMapB.build();

        ImmutableList.Builder<UIJob> b = ImmutableList.builder();
        TownFlagBlockEntity unsafeTown = town.getUnsafe();
        ImmutableMap<JobID, ResourceLocation> jubz = ServerJobsRegistry.getAllJobsThatProduce(
                town.town.getTownData(),
                itemToShowJobsFor
        );
        for (JobID job : jubz.keySet()) {
            Supplier<Work> w = Works.get(job);
            Work gotWork = w.get();
            Job<?, ?, ?> j = gotWork.jobFunc.apply(UUID.randomUUID());
            if (!(j instanceof DeclarativeJob dj)) {
                continue;
            }

            RoomRecipe r = rMap.get(dj.location().baseRoom());
            b.add(new UIJob(
                    j.getId(),
                    ImmutableList.copyOf(vb.values().stream().flatMap(Collection::stream).collect(Collectors.toSet())),
                    ImmutableList.copyOf(dj.initialIngredients.values()),
                    ImmutableList.copyOf(dj.initialTools.values()),
                    dj.location().baseRoom(),
                    r == null ? ImmutableList.of() : ImmutableList.copyOf(r.getIngredients()),
                    ImmutableList.copyOf(gotWork.results.apply(unsafeTown.getTownData()).stream()
                                                        .map(v -> v.get().getDefaultInstance()).toList())
            ));
        }
        Object msg = new ShowItemJobsMessage(itemToShowJobsFor, b.build(), unsafeTown.getTownFlagBasePos());
        QuestownNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sender), msg);
    }

    @Override
    public void register(VisitorMobEntity vEntity) {
        @NotNull TownFlagBlockEntity t = town.getUnsafe();
        QT.FLAG_LOGGER.debug("Registered entity with town {}: {}", t.getUUID(), vEntity);
        add(vEntity);
        vEntity.addChangeListener(() -> {
            QT.FLAG_LOGGER.trace("Entity requests flag to be marked changed");
            t.setChanged();
        });
        t.setChanged();
    }

    @Override
    public void unlockJob(
            UUID villagerUUID,
            JobID id
    ) {
        UtilClean.addOrInitialize(unlockedJobs, villagerUUID, id);
    }

    @Override
    public void addExperience(
            UUID uuid,
            int exp
    ) {
        experience.compute(uuid, (x, cur) -> cur == null ? exp : cur + exp);
    }
}

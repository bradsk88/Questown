package ca.bradj.questown.town;

import ca.bradj.questown.QT;
import ca.bradj.questown.core.UtilClean;
import ca.bradj.questown.core.advancements.RoomTrigger;
import ca.bradj.questown.core.init.AdvancementsInit;
import ca.bradj.questown.core.network.*;
import ca.bradj.questown.gui.*;
import ca.bradj.questown.jobs.*;
import ca.bradj.questown.mc.Compat;
import ca.bradj.questown.mobs.visitor.VisitorMobEntity;
import ca.bradj.questown.town.econ.NoMCEconomics;
import ca.bradj.questown.town.entity.TownFlagBlockEntity;
import ca.bradj.questown.town.quests.MCReward;
import ca.bradj.questown.town.quests.Quest;
import ca.bradj.questown.town.special.SpecialQuests;
import ca.bradj.roomrecipes.recipes.RecipesInit;
import ca.bradj.roomrecipes.recipes.RoomRecipe;
import ca.bradj.roomrecipes.serialization.MCRoom;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import joptsimple.internal.Strings;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.network.PacketDistributor;
import org.apache.commons.lang3.function.TriFunction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class TownVillagerUIs {

    public static void showMultiStatusUI(
            ServerPlayer player,
            FlagTabsEmbedding.FlagInfo townFlagBasePos,
            Collection<LivingEntity> entities,
            Supplier<Collection<? extends Map.Entry<? extends Quest<ResourceLocation, MCRoom>, MCReward>>> questsSrc,
            int bopCount
    ) {
        List<VisitorMobEntity> es = entities.stream().map(v -> (VisitorMobEntity) v).toList();

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
                        if (entities.isEmpty()) {
                            return;
                        }
                        AdvancementsInit.ROOM_TRIGGER.triggerForNearestPlayer(
                                player.getLevel(),
                                RoomTrigger.Triggers.FirstOpenFlagMenu,
                                townFlagBasePos.flagPos()
                        );
                    }
                }, data -> {
                    List<UIQuest> quests = UIQuest.fromLevel(
                            player.getLevel(),
                            questsSrc.get()
                    );
                    FlagMenus.writeAndLink(data, quests, townFlagBasePos, player, es, bopCount);
                }
        );
    }

    public static void showItemJobsUI(
            ServerPlayer sender,
            TownFlagBlockEntity unsafeTown,
            Collection<LivingEntity> entities,
            Ingredient itemToShowJobsFor
    ) {
        Map<JobID, List<UUID>> vb = new HashMap<>();
        for (LivingEntity entity : entities) {
            if (!(entity instanceof VisitorMobEntity vme)) {
                continue;
            }
            UtilClean.addOrInitializeList(vb, vme.getJobId(), vme.getUUID());
        }

        ImmutableMap.Builder<ResourceLocation, RoomRecipe> rMapB = ImmutableMap.builder();
        SpecialQuests.SPECIAL_QUESTS.forEach(rMapB::put);
        sender.getLevel().getRecipeManager().getAllRecipesFor(RecipesInit.ROOM).forEach(v -> rMapB.put(v.getId(), v));
        ImmutableMap<ResourceLocation, RoomRecipe> rMap = rMapB.build();

        ImmutableList.Builder<UIJob> b = ImmutableList.builder();
        ImmutableMap<JobID, ResourceLocation> jubz = ServerJobsRegistry.getAllJobsThatProduce(
                unsafeTown.getTownData(),
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

    public static void showUI(
            ServerPlayer sender,
            Collection<LivingEntity> entities,
            String type,
            UUID villagerId,
            Map<UUID, ? extends Set<JobID>> unlockedJobs,
            Set<JobID> unlockableJobs
    ) {
        Optional<LivingEntity> f = entities.stream().filter(VisitorMobEntity.class::isInstance)
                                           .filter(v -> villagerId.equals(v.getUUID())).findFirst();
        if (f.isEmpty()) {
            QT.FLAG_LOGGER.error("No villagers with ID {} while opening UI", villagerId);
            return;
        }

        syncWorkToClient(sender);

        VisitorMobEntity e = (VisitorMobEntity) f.get();

        TownFlagBlockEntity flag = TownFlagBlockEntity.getFromPos(sender.level, e.getFlagPos());

        List<UIQuest> quests = flag.getQuestHandle().getAllBatchesForVillager(e.getUUID()).stream()
                                   .map(v -> UIQuest.fromLevel(sender.getLevel(), v)).flatMap(List::stream).toList();

        VillagerStatsData stats = flag.getVillagerHandle().getStats(e.getUUID());
        VillagerEconomicsData econ = new VillagerEconomicsData(ImmutableList.of());

        Consumer<ShowerData> runnable = menuShow.get(type);
        if (runnable == null) {
            throw new IllegalArgumentException("Unexpected menu type: \"" + type + "\"");
        }
        ShowerData d = new ShowerData(
                sender,
                quests,
                e,
                stats,
                econ,
                flag::getEconomicsHandle,
                unlockedJobs,
                unlockableJobs,
                villagerId
        );
        runnable.accept(d);
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

    record ShowerData(
            ServerPlayer sender, List<UIQuest> quests, VisitorMobEntity entity,
            VillagerStatsData stats, VillagerEconomicsData econ, Supplier<NoMCEconomics> econHandle,
            Map<UUID, ? extends Set<JobID>> unlockedJobs,
            Set<JobID> unlockableJobs,
            UUID villagerId) {
    }

    private static ImmutableMap<String, Consumer<ShowerData>> menuShow;

    public static void staticInit() {
        ImmutableMap.Builder<String, Consumer<ShowerData>> b = ImmutableMap.builder();
        b.put(
                OpenVillagerMenuMessage.INVENTORY,
                (ShowerData d) -> openMenu(
                        d.sender(), (windowId, inv, p) -> {
                            VisitorMobEntity e = d.entity();
                            InventoryAndStatusMenu x = new InventoryAndStatusMenu(
                                    windowId,
                                    e.getInventory(),
                                    p.getInventory(),
                                    e.getSlotLocks(),
                                    e.getUUID(),
                                    e.getJobId(),
                                    e.getFlagPos(),
                                    e.hasBlockOfProgress()
                            );
                            x.connectToServer(e, d.sender());
                            return x;
                        }, d.quests(), d.entity(), d.stats()
                )
        );
        b.put(
                OpenVillagerMenuMessage.QUESTS, (ShowerData d) -> {
                    VisitorMobEntity e = d.entity();
                    openMenu(
                            d.sender(),
                            (windowId, inv, p) -> new VillagerQuestsContainer(
                                    windowId,
                                    e.getUUID(),
                                    d.quests(),
                                    e.getFlagPos(),
                                    e.hasBlockOfProgress()
                            ),
                            d.quests(),
                            e,
                            d.stats()
                    );
                }
        );
        b.put(
                OpenVillagerMenuMessage.STATS, (ShowerData d) -> {
                    VisitorMobEntity e = d.entity();
                    openMenu(
                            d.sender(),
                            (windowId, inv, p) -> new VillagerStatsMenu(
                                    windowId,
                                    e,
                                    e.getFlagPos(),
                                    d.stats(),
                                    e.hasBlockOfProgress()
                            ),
                            d.quests(),
                            e,
                            d.stats()
                    );
                }
        );
        b.put(
                OpenVillagerMenuMessage.SKILLS, (ShowerData d) -> {
                    Collection<JobID> vUnlocked = UtilClean.getOrDefaultCollection(
                            d.unlockedJobs(),
                            d.entity().getUUID(),
                            ImmutableList.of()
                    );
                    OpenVillagerAdvancementsMenuMessage msg = new OpenVillagerAdvancementsMenuMessage(
                            d.entity()
                             .getFlagPos(),
                            d.entity().getUUID(),
                            vUnlocked,
                            d.unlockableJobs,
                            d.entity().getJobId(),
                            d.entity().hasBlockOfProgress()
                    );
                    QuestownNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(d::sender), msg);
                }
        );
        b.put(
                OpenVillagerMenuMessage.CHANGE_ROOT, (ShowerData d) -> {
                    openMenu(
                            d.sender(), (windowId, inv, p) -> new JobChangeConfirmMenu(
                                    windowId,
                                    new SimpleContainer(1) {
                                        @Override
                                        public int getMaxStackSize() {
                                            return 1;
                                        }
                                    },
                                    d.sender.getInventory(),
                                    d.entity.getUUID(),
                                    d.entity.getJobId(),
                                    d.entity().getFlagPos(),
                                    d.entity.isJobChangePending()
                            ), d.quests(), d.entity(), d.stats()
                    );
                }
        );
        b.put(
                OpenVillagerMenuMessage.ECONOMICS, (ShowerData d) -> {
                    NoMCEconomics tEcon = d.econHandle.get();
                    ImmutableList<ItemEconomicsData> aggregated = tEcon.getAggregatedItems(d.villagerId());
                    QuestownNetwork.CHANNEL.send(
                            PacketDistributor.PLAYER.with(d::sender),
                            new EconomicsUpdate(aggregated)
                    );
                    openMenu(
                            d.sender(), (windowId, inv, p) -> new VillagerEconomicsMenu(
                                    windowId,
                                    d.entity(),
                                    d.entity().getFlagPos(),
                                    d.econ(),
                                    d.entity().hasBlockOfProgress()
                            ), d.quests(), d.entity(), d.stats()
                    );
                }
        );
        b.put(
                OpenVillagerMenuMessage.BOP, (ShowerData d) -> {
                    NoMCEconomics tEcon = d.econHandle.get();
                    ImmutableList<ItemEconomicsData> aggregated = tEcon.getAggregatedItems(d.villagerId());
                    QuestownNetwork.CHANNEL.send(
                            PacketDistributor.PLAYER.with(d::sender),
                            new EconomicsUpdate(aggregated)
                    );
                    openMenu(
                            d.sender(), (windowId, inv, p) -> new VillagerBlockofProgressMenu(
                                    windowId,
                                    d.entity().getUUID(),
                                    d.entity().getFlagPos()
                            ), d.quests(), d.entity(), d.stats()
                    );
                }
        );

        menuShow = b.build();
        ArrayList<String> a = new ArrayList<>(VillagerTabs.all());
        a.removeAll(menuShow.keySet());
        if (a.isEmpty()) {
            return;
        }
        throw new IllegalStateException("Missing MenuShow entry for [" + Strings.join(a, ", ") + "]");
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
                        new VillagerEconomicsData(ImmutableList.of()),
                        e.hasBlockOfProgress(),
                        e.isJobChangePending()
                )
        );
    }

}

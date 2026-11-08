package ca.bradj.questown.town;

import net.minecraft.server.packs.resources.ResourceManager;
import ca.bradj.questown.jobs.gatherer.Loots;
import ca.bradj.questown.loader.Loader;
import ca.bradj.questown.jobs.Works;
import ca.bradj.questown.QT;
import ca.bradj.questown.blocks.RoomBlock;
import ca.bradj.questown.core.UtilClean;
import ca.bradj.questown.core.VillagerUUID;
import ca.bradj.questown.core.network.*;
import ca.bradj.questown.gui.*;
import ca.bradj.questown.integration.minecraft.MCTownItem;
import ca.bradj.questown.jobs.*;
import ca.bradj.questown.jobs.declarative.DeclarativeJob;
import ca.bradj.questown.mc.Compat;
import ca.bradj.questown.mobs.visitor.VisitorMobEntity;
import ca.bradj.questown.town.econ.NoMCEconomics;
import ca.bradj.questown.town.entity.TownFlagBlockEntity;
import ca.bradj.questown.town.special.SpecialQuests;
import ca.bradj.roomrecipes.recipes.RecipesInit;
import ca.bradj.roomrecipes.recipes.RoomRecipe;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import org.apache.commons.lang3.function.TriFunction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class TownVillagerUIs {

    public static void showItemJobsUI(
            ServerPlayer sender,
            TownFlagBlockEntity unsafeTown,
            Collection<? extends LivingEntity> entities,
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
        // Jobs that make the item, not jobs that only ask for it.
        ImmutableMap<JobID, ResourceLocation> jubz = ServerJobsRegistry.getJobsKnownToProduce(
                unsafeTown.getTownDataForDisplay(),
                itemToShowJobsFor
        );
        QT.GUI_LOGGER.dev("[item-jobs] producers of {}: {}", Arrays.toString(itemToShowJobsFor.getItems()), jubz.keySet());
        for (JobID job : jubz.keySet()) {
            Job<?, ?, ?> j = ServerJobsRegistry.getUninitializedJob(job, VillagerUUID.random()).getJob();
            if (!(j instanceof DeclarativeJob dj)) {
                continue;
            }

            ImmutableSet<MCTownItem> results = ServerJobsRegistry.getResults(unsafeTown.getTownDataForDisplay(), job);
            ImmutableList<Ingredient> roomRecipe = getRoomIngredients(dj, rMap);

            b.add(new UIJob(
                    j.getId(),
                    ImmutableList.copyOf(vb.values().stream().flatMap(Collection::stream).collect(Collectors.toSet())),
                    ImmutableList.copyOf(dj.initialIngredients.values()),
                    ImmutableList.copyOf(dj.initialTools.values()),
                    dj.location().baseRoom(),
                    roomRecipe,
                    results.stream()
                           .map(v -> v.get().getDefaultInstance())
                           .collect(ImmutableList.toImmutableList())
            ));
        }
        Object msg = new ShowItemJobsMessage(itemToShowJobsFor, b.build(), unsafeTown.getTownFlagBasePos());
        QuestownNetwork.sendToPlayer(sender, msg);
    }

    /** What a skill's details list under "Produces": a trip job's default table, most likely first; any other job, its results. */
    private static ImmutableList<ItemStack> skillResults(
            TownFlagBlockEntity unsafe,
            JobID job
    ) {
        String table = Works.isInitialized() && Works.ids().contains(job) ? Works.get(job).get().lootTableDefault() : null;
        if (table == null) {
            return ServerJobsRegistry.getResults(unsafe.getTownDataForDisplay(), job).stream()
                                     .map(v -> v.get().getDefaultInstance())
                                     .collect(ImmutableList.toImmutableList());
        }
        ResourceManager rm = unsafe.getServerLevel().getServer().getResourceManager();
        ImmutableList.Builder<ItemStack> b = ImmutableList.builder();
        for (String id : Loots.getMostLikelyItems(rm, table)) {
            net.minecraft.world.item.Item item = Loader.itemFromId(new ResourceLocation(id));
            if (item != null) {
                b.add(item.getDefaultInstance());
            }
        }
        return b.build();
    }

    private static @NotNull ImmutableList<Ingredient> getRoomIngredients(
            DeclarativeJob dj,
            ImmutableMap<ResourceLocation, RoomRecipe> rMap
    ) {
        RoomRecipe r = rMap.get(dj.location().baseRoom());
        ImmutableList<Ingredient> roomRecipe;
        @Nullable ItemStack roomBlock = RoomBlock.getRoomBlock(dj.location().baseRoom());
        if (roomBlock != null) {
            roomRecipe = ImmutableList.of(Ingredient.of(roomBlock));
        } else {
            roomRecipe = r == null ? ImmutableList.of() : ImmutableList.copyOf(r.getIngredients());
        }
        return roomRecipe;
    }

    public static void showJobsWithSameRootUI(
            ServerPlayer sender,
            @NotNull TownFlagBlockEntity unsafe,
            Collection<? extends LivingEntity> entities,
            JobID childJob
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

        // Only jobs the town knows.
        ImmutableList<JobID> jubz = ServerJobsRegistry.getAllJobs()
                                                      .stream()
                                                      .filter(v -> v.sameRoot(childJob))
                                                      .filter(unsafe.getVillagerHandle()::isUnlocked)
                                                      .collect(ImmutableList.toImmutableList());
        ImmutableList.Builder<UIJob> b = ImmutableList.builder();
        for (JobID job : jubz) {
            UIJob card = uiJob(unsafe, vb, job, rMap);
            if (card != null) {
                b.add(card);
            }
        }

        Object msg = new ShowItemJobsMessage(Ingredient.of(ItemStack.EMPTY), b.build(), unsafe.getTownFlagBasePos());
        QuestownNetwork.sendToPlayer(sender, msg);
    }

    /** Sends one skill's details, and whether it is still locked, so the screen offers Unlock or Switch. */
    public static void showSkillDetailsUI(
            ServerPlayer sender,
            @NotNull TownFlagBlockEntity unsafe,
            Collection<? extends LivingEntity> entities,
            JobID job,
            UUID villager,
            boolean locked,
            int flagBops
    ) {
        Map<JobID, List<UUID>> vb = new HashMap<>();
        for (LivingEntity entity : entities) {
            if (entity instanceof VisitorMobEntity vme) {
                UtilClean.addOrInitializeList(vb, vme.getJobId(), vme.getUUID());
            }
        }
        UIJob card = uiJob(unsafe, vb, job, roomRecipes(sender));
        if (card == null) {
            QT.GUI_LOGGER.error("No details for skill {}", job);
            return;
        }
        QuestownNetwork.sendToPlayer(sender, new ShowSkillDetailsMessage(card, unsafe.getTownFlagBasePos(), villager, locked, flagBops));
    }

    private static ImmutableMap<ResourceLocation, RoomRecipe> roomRecipes(ServerPlayer sender) {
        ImmutableMap.Builder<ResourceLocation, RoomRecipe> rMapB = ImmutableMap.builder();
        SpecialQuests.SPECIAL_QUESTS.forEach(rMapB::put);
        sender.getLevel().getRecipeManager().getAllRecipesFor(RecipesInit.ROOM).forEach(v -> rMapB.put(v.getId(), v));
        return rMapB.build();
    }

    private static @Nullable UIJob uiJob(
            TownFlagBlockEntity unsafe,
            Map<JobID, List<UUID>> villagersByJob,
            JobID job,
            ImmutableMap<ResourceLocation, RoomRecipe> rMap
    ) {
        Job<?, ?, ?> j = ServerJobsRegistry.getUninitializedJob(job, VillagerUUID.random()).getJob();
        if (!(j instanceof DeclarativeJob dj)) {
            return null;
        }
        return new UIJob(
                j.getId(),
                ImmutableList.copyOf(villagersByJob.values().stream().flatMap(Collection::stream).collect(Collectors.toSet())),
                ImmutableList.copyOf(dj.initialIngredients.values()),
                ImmutableList.copyOf(dj.initialTools.values()),
                dj.location().baseRoom(),
                getRoomIngredients(dj, rMap),
                skillResults(unsafe, job)
        );
    }

    public static void showUI(
            ServerPlayer sender,
            Collection<? extends LivingEntity> entities,
            String type,
            UUID villagerId,
            Map<UUID, ? extends Set<JobID>> unlockedJobs,
            Set<JobID> unlockableJobs
    ) {
        showUI(sender, entities, type, villagerId, unlockedJobs, unlockableJobs, true);
    }

    /** Opened from the flag, without the inventory tab; a request for the inventory opens stats instead. */
    public static void showUI(
            ServerPlayer sender,
            Collection<? extends LivingEntity> entities,
            String type,
            UUID villagerId,
            Map<UUID, ? extends Set<JobID>> unlockedJobs,
            Set<JobID> unlockableJobs,
            boolean showInventoryTab
    ) {
        if (!showInventoryTab && OpenVillagerMenuMessage.INVENTORY.equals(type)) {
            type = OpenVillagerMenuMessage.STATS;
        }
        Optional<VisitorMobEntity> f = entities.stream()
                                               .filter(VisitorMobEntity.class::isInstance)
                                               .map(v -> (VisitorMobEntity) v)
                                               .filter(v -> villagerId.equals(v.getUUID())).findFirst();
        if (f.isEmpty()) {
            QT.FLAG_LOGGER.error("No villagers with ID {} while opening UI", villagerId);
            return;
        }

        syncWorkToClient(sender);

        VisitorMobEntity e = f.get();
        if (OpenVillagerMenuMessage.INITIAL.equals(type)) {
            type = VillagerTabs.initialTab(wantsJobChange(sender, e), showInventoryTab);
        }

        TownFlagBlockEntity flag = TownFlagBlockEntity.getFromPos(sender.getLevel(), e.getFlagPos());

        List<UIQuest> quests = flag.getQuestHandle().getAllBatchesForVillager(e.getUUID()).stream()
                                   .map(v -> UIQuest.fromLevel(sender.getLevel(), v)).flatMap(List::stream).toList();

        VillagerStatsData stats = flag.getVillagerHandle().getStats(e.getUUID());
        VillagerEconomicsData econ = new VillagerEconomicsData(ImmutableList.of());

        Consumer<ShowerData> runnable = menuShow.get(type);
        if (runnable == null) {
            // The type comes from the client: reject an unknown one.
            QT.GUI_LOGGER.error("Unexpected menu type: \"{}\"", type);
            return;
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
                villagerId,
                flag.getVillagerHandle().isJobChangePending(e.getVUID()),
                showInventoryTab,
                flag.getVillagerHandle().displayNameFor(villagerId)
        );
        runnable.accept(d);
    }

    /** Sends every job's parent and icon to the client; on join too, since quest cards need the icons. */
    public static void syncWorkToClient(ServerPlayer sender) {
        Map<JobID, @Nullable JobID> b = new HashMap<>();
        Map<JobID, ResourceLocation> b2 = new HashMap<>();
        Works.values().forEach(w -> {
            Work work = w.get();
            b.put(work.id, work.parentID);
            b2.put(work.id, Compat.getItemId(work.icon.getItem()));
        });
        QuestownNetwork.sendToPlayer(sender, new SyncVillagerAdvancementsMessage(b, b2));
    }

    record ShowerData(
            ServerPlayer sender, List<UIQuest> quests, VisitorMobEntity entity,
            VillagerStatsData stats, VillagerEconomicsData econ, Supplier<NoMCEconomics> econHandle,
            Map<UUID, ? extends Set<JobID>> unlockedJobs,
            Set<JobID> unlockableJobs,
            UUID villagerId,
            boolean jobChangePending,
            boolean showInventoryTab,
            /** The villager's "Villager N" name; empty when it has none. */
            String villagerDisplayName
    ) {
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
                                    e.hasBlockOfProgress(),
                                    !d.unlockableJobs().isEmpty(),
                                    d.showInventoryTab()
                            );
                            x.connectToServer(e, d.sender());
                            return x;
                        }, d.quests(), d.entity(), d.stats(), d.jobChangePending(), !d.unlockableJobs().isEmpty(), d.showInventoryTab(), d.villagerDisplayName()
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
                                    e.hasBlockOfProgress(),
                                    !d.unlockableJobs().isEmpty(),
                                    d.showInventoryTab()
                            ),
                            d.quests(),
                            e,
                            d.stats(),
                            d.jobChangePending(),
                            !d.unlockableJobs().isEmpty(),
                            d.showInventoryTab(),
                            d.villagerDisplayName()
                    );
                }
        );
        b.put(
                OpenVillagerMenuMessage.STATS, (ShowerData d) -> {
                    VisitorMobEntity e = d.entity();
                    openMenu(
                            d.sender(),
                            (windowId, inv, p) -> {
                                VillagerStatsMenu m = new VillagerStatsMenu(
                                        windowId,
                                        e.getUUID(),
                                        e.getFlagPos(),
                                        d.stats(),
                                        e.hasBlockOfProgress(),
                                        !d.unlockableJobs().isEmpty(),
                                        d.showInventoryTab()
                                );
                                m.attach(e);
                                return m;
                            },
                            d.quests(),
                            e,
                            d.stats(),
                            d.jobChangePending(),
                            !d.unlockableJobs().isEmpty(),
                            d.showInventoryTab(),
                            d.villagerDisplayName()
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
                            d.entity().hasBlockOfProgress(),
                            d.showInventoryTab()
                    );
                    QuestownNetwork.sendToPlayer(d.sender(), msg);
                }
        );
        b.put(
                OpenVillagerMenuMessage.CHANGE_ROOT, (ShowerData d) -> openMenu(
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
                                d.jobChangePending(),
                                !d.unlockableJobs().isEmpty(),
                                d.showInventoryTab(),
                                promisedRoot(d.sender(), d.entity()),
                                JobChangeConfirmMenu.FlagBop.NONE
                        ), d.quests(), d.entity(), d.stats(), d.jobChangePending(), !d.unlockableJobs().isEmpty(), d.showInventoryTab(), d.villagerDisplayName()
                )
        );
        b.put(
                OpenVillagerMenuMessage.ECONOMICS, (ShowerData d) -> {
                    NoMCEconomics tEcon = d.econHandle.get();
                    ImmutableList<ItemEconomicsData> aggregated = tEcon.getAggregatedItems(d.villagerId());
                    QuestownNetwork.sendToPlayer(d.sender(), new EconomicsUpdate(aggregated));
                    openMenu(
                            d.sender(), (windowId, inv, p) -> new VillagerEconomicsMenu(
                                    windowId,
                                    d.entity().getUUID(),
                                    d.entity().getFlagPos(),
                                    d.econ(),
                                    d.entity().hasBlockOfProgress(),
                                    !d.unlockableJobs().isEmpty(),
                                    d.showInventoryTab()
                            ), d.quests(), d.entity(), d.stats(), d.jobChangePending(), !d.unlockableJobs().isEmpty(), d.showInventoryTab(), d.villagerDisplayName()
                    );
                }
        );
        b.put(
                OpenVillagerMenuMessage.BOP, (ShowerData d) -> {
                    NoMCEconomics tEcon = d.econHandle.get();
                    ImmutableList<ItemEconomicsData> aggregated = tEcon.getAggregatedItems(d.villagerId());
                    QuestownNetwork.sendToPlayer(d.sender(), new EconomicsUpdate(aggregated));
                    openMenu(
                            d.sender(), (windowId, inv, p) -> new VillagerBlockofProgressMenu(
                                    windowId,
                                    d.entity().getUUID(),
                                    d.entity().getFlagPos(),
                                    !d.unlockableJobs().isEmpty(),
                                    d.showInventoryTab(),
                                    d.villagerDisplayName()
                            ), d.quests(), d.entity(), d.stats(), d.jobChangePending(), !d.unlockableJobs().isEmpty(), d.showInventoryTab(), d.villagerDisplayName()
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


    private static boolean wantsJobChange(ServerPlayer sender, VisitorMobEntity e) {
        if (e.getFlagPos() == null) {
            return false;
        }
        TownFlagBlockEntity flag = TownFlagBlockEntity.getFromPos(sender.getLevel(), e.getFlagPos());
        return flag != null && flag.wantsJobChangeFrom(e.getJobId());
    }

    /** The flag's Blocks of Progress, for a panel opened from the flag. */
    private static JobChangeConfirmMenu.FlagBop flagBop(ServerPlayer sender, VisitorMobEntity e) {
        TownFlagBlockEntity flag = e.getFlagPos() == null ? null : TownFlagBlockEntity.getFromPos(sender.getLevel(), e.getFlagPos());
        if (flag == null) {
            return JobChangeConfirmMenu.FlagBop.NONE;
        }
        return new JobChangeConfirmMenu.FlagBop(flag.getBopCount());
    }

    private static boolean hidesJobChange(ServerPlayer sender, VisitorMobEntity e) {
        if (e.getFlagPos() == null) {
            return false;
        }
        TownFlagBlockEntity flag = TownFlagBlockEntity.getFromPos(sender.getLevel(), e.getFlagPos());
        return flag != null && flag.hidesJobChangeFrom(e.getJobId());
    }

    private static String promisedRoot(ServerPlayer sender, VisitorMobEntity e) {
        if (e.getFlagPos() == null) {
            return "";
        }
        TownFlagBlockEntity flag = TownFlagBlockEntity.getFromPos(sender.getLevel(), e.getFlagPos());
        if (flag == null) {
            return "";
        }
        return flag.promisedJobChange(e.getUUID()).map(JobID::rootId).orElse("");
    }

    private static void openMenu(
            ServerPlayer sender,
            TriFunction<Integer, Inventory, Player, AbstractContainerMenu> shower,
            List<UIQuest> quests,
            VisitorMobEntity e,
            VillagerStatsData stats,
            boolean isJobChangePending,
            boolean showSkillsTab,
            boolean showInventoryTab,
            String villagerDisplayName
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
                        showSkillsTab,
                        showInventoryTab,
                        isJobChangePending,
                        villagerDisplayName,
                        wantsJobChange(sender, e),
                        promisedRoot(sender, e),
                        !hidesJobChange(sender, e),
                        showInventoryTab ? JobChangeConfirmMenu.FlagBop.NONE : flagBop(sender, e)
                )
        );
        // TODO: Opening a villager's menu counted for the helper chicken's lesson. For its implementation see #409, #410, #411 and #412.
    }

}

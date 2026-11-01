package ca.bradj.questown.town;

import net.minecraft.server.packs.resources.ResourceManager;
import ca.bradj.questown.loader.Loader;
import ca.bradj.questown.jobs.gatherer.Loots;
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
        sender.level().getRecipeManager().getAllRecipesFor(RecipesInit.ROOM).forEach(v -> rMapB.put(v.id(), v.value()));
        ImmutableMap<ResourceLocation, RoomRecipe> rMap = rMapB.build();

        ImmutableList.Builder<UIJob> b = ImmutableList.builder();
        // Producers by what they produce (their results, knowledge-limited for loot jobs), not by
        // their declared initial_request: cook/fuel, cook/stock_fuel, cook/stock_ingredients and
        // baker/fuel declare #villager_food but only consume it (2026-10-19 playtest: the "8 food"
        // quest's producers list showed only jobs that USE cooked food).
        ImmutableMap<JobID, ResourceLocation> jubz = ServerJobsRegistry.getJobsKnownToProduce(
                unsafeTown.getTownDataForDisplay(),
                itemToShowJobsFor
        );
        QT.GUI_LOGGER.info("[item-jobs] producers of {}: {}", Arrays.toString(itemToShowJobsFor.getItems()), jubz.keySet());
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

    /**
     * What a skill's details card lists under "Produces:". A loot job lists its default table's
     * items, most likely first (dev 2026-10-30: a hunter's list was empty, since only loot the town
     * has already found counted); any other job lists its results.
     */
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
        sender.level().getRecipeManager().getAllRecipesFor(RecipesInit.ROOM).forEach(v -> rMapB.put(v.id(), v.value()));
        ImmutableMap<ResourceLocation, RoomRecipe> rMap = rMapB.build();

        // Day-1 playtest (villager-jobs-tooltip-shows-all-jobs.md): this list advertised the
        // whole same-root catalogue. Filter to the jobs the town actually knows - the same
        // town-wide "can anyone in town do this" gate the request board uses.
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

    /**
     * One skill's details for the skill tree (dev playtest 2026-10-29: "Clicking on a skill ... should
     * take the player to a variant of the existing 'who makes <item>' screen"): the job's card, sent
     * back with the villager and whether the skill is still locked so the screen offers Unlock or
     * Switch.
     */
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
        sender.level().getRecipeManager().getAllRecipesFor(RecipesInit.ROOM).forEach(v -> rMapB.put(v.id(), v.value()));
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

    /**
     * Management-only variant ({@code showInventoryTab=false}): the flag's villager-card
     * click-through opens the menu without the inventory tab. A request for the inventory
     * pane itself is redirected to the stats pane (the management view) so the player is
     * not dropped into a pane whose tab no longer exists.
     */
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

        TownFlagBlockEntity flag = TownFlagBlockEntity.getFromPos(sender.level(), e.getFlagPos());

        List<UIQuest> quests = flag.getQuestHandle().getAllBatchesForVillager(e.getUUID()).stream()
                                   .map(v -> UIQuest.fromLevel(sender.serverLevel(), v)).flatMap(List::stream).toList();

        VillagerStatsData stats = flag.getVillagerHandle().getStats(e.getUUID());
        VillagerEconomicsData econ = new VillagerEconomicsData(ImmutableList.of());

        Consumer<ShowerData> runnable = menuShow.get(type);
        if (runnable == null) {
            // The type comes from a client packet: reject an unknown one instead of throwing in the
            // network handler (2026-10-21: a dev-tool "bop" for "blocks_of_progress").
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

    /**
     * Sends every job's parent and icon (the skill tree and job names read them). On join too
     * (Outfitter #19): a quest card's item-named skill reads the icon before any villager menu opens.
     */
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
            /** "Villager N" display name (empty when unassigned - screens fall back to the UUID). */
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
                            // Management-only sessions (opened from the flag menu)
                            // keep the inventory tab hidden on the skills pane too -
                            // the advancements screen's tab embedding used to return
                            // VillagerTabs.all(), resurrecting the tab (2026-10-15
                            // playtest: "the flag menu -> villager menu screen still
                            // shows the inventory tab").
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
        TownFlagBlockEntity flag = TownFlagBlockEntity.getFromPos(sender.level(), e.getFlagPos());
        return flag != null && flag.wantsJobChangeFrom(e.getJobId());
    }

    /** The flag's Blocks of Progress for a panel opened from the flag (management-only). */
    private static JobChangeConfirmMenu.FlagBop flagBop(ServerPlayer sender, VisitorMobEntity e) {
        if (e.getFlagPos() == null
                || !(TownFlagBlockEntity.getFromPos(sender.level(), e.getFlagPos()) instanceof TownFlagBlockEntity flag)) {
            return JobChangeConfirmMenu.FlagBop.NONE;
        }
        return new JobChangeConfirmMenu.FlagBop(flag.getBopCount());
    }

    private static boolean hidesJobChange(ServerPlayer sender, VisitorMobEntity e) {
        if (e.getFlagPos() == null) {
            return false;
        }
        TownFlagBlockEntity flag = TownFlagBlockEntity.getFromPos(sender.level(), e.getFlagPos());
        return flag != null && flag.hidesJobChangeFrom(e.getJobId());
    }

    private static String promisedRoot(ServerPlayer sender, VisitorMobEntity e) {
        if (e.getFlagPos() == null) {
            return "";
        }
        TownFlagBlockEntity flag = TownFlagBlockEntity.getFromPos(sender.level(), e.getFlagPos());
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
        if (e.getFlagPos() != null) {
            ca.bradj.questown.mobs.helperchicken.ChickenArcUiObservations.markVillagerUiOpened(
                    sender.serverLevel(), e.getFlagPos()
            );
        }
    }

}

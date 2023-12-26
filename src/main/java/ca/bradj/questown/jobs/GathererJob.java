package ca.bradj.questown.jobs;

import ca.bradj.questown.QT;
import ca.bradj.questown.Questown;
import ca.bradj.questown.core.init.TagsInit;
import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.integration.minecraft.MCTownItem;
import ca.bradj.questown.jobs.gatherer.GathererTools;
import ca.bradj.questown.jobs.leaver.LeaverJob;
import ca.bradj.questown.mobs.visitor.VisitorMobEntity;
import ca.bradj.questown.town.interfaces.TownInterface;
import com.google.common.collect.ImmutableList;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.LootTables;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Function;

public class GathererJob extends LeaverJob {

    public static final JobID ID = new JobID("gatherer", "gather");

    public GathererJob(
            TownInterface town,
            // null on client side
            int inventoryCapacity,
            UUID ownerUUID
    ) {
        super(town, inventoryCapacity, ownerUUID);
    }

    @Override
    protected GathererJournal<MCTownItem, MCHeldItem> constructorJournal(
            SignalSource signalSource,
            GathererStatuses.TownStateProvider tsp,
            int inventoryCapacity
    ) {
        return new GathererJournal<MCTownItem, MCHeldItem>(
                signalSource, MCHeldItem::Air,
                tsp, inventoryCapacity, GathererJob::checkTools
        ) {
            @Override
            protected void changeStatus(Status s) {
                super.changeStatus(s);
                QT.JOB_LOGGER.debug("Changed status to {}", s);
            }
        };
    }

    public static GathererJournal.Tools checkTools(Iterable<MCHeldItem> journalItems) {
        GathererJournal.Tools tool = new GathererJournal.Tools(false, false, false, false);
        for (MCHeldItem item : journalItems) {
            if (Ingredient.of(TagsInit.Items.AXES).test(item.get().toItemStack())) {
                tool = tool.withAxe();
            }
            if (Ingredient.of(TagsInit.Items.PICKAXES).test(item.get().toItemStack())) {
                tool = tool.withPickaxe();
            }
            if (Ingredient.of(TagsInit.Items.SHOVELS).test(item.get().toItemStack())) {
                tool = tool.withShovel();
            }
            if (Ingredient.of(TagsInit.Items.FISHING_RODS).test(item.get().toItemStack())) {
                tool = tool.withFishingRod();
            }
        }
        return tool;
    }

    @Override
    public Collection<MCHeldItem> getLoot(GathererJournal.Tools tools) {
        return getLootFromLevel(town, journal.getCapacity(), tools, computeBiome(journal.getItems(), town));
    }

    public static ResourceLocation computeBiome(
            Iterable<MCHeldItem> items,
            TownInterface town
    ) {
        ResourceLocation biome = null;
        // TODO[ASAP]: Bring back
//        for (MCHeldItem item : items) {
//            if (item.get().get().equals(ItemsInit.GATHERER_MAP.get())) {
//                biome = GathererMap.getBiome(item.get().toItemStack());
//                if (biome == null) {
//                    QT.JOB_LOGGER.error("No biome tag on gatherer map. Ignoring");
//                    continue;
//                }
//                break;
//            }
//        }
        if (biome == null) {
            biome = town.getRandomNearbyBiome();
        }
        return biome;
    }

    public static Collection<MCHeldItem> getLootFromLevel(
            TownInterface town,
            int maxItems,
            GathererJournal.Tools tools,
            ResourceLocation biome
    ) {
        if (town == null || town.getServerLevel() == null) {
            return ImmutableList.of();
        }
        ServerLevel level = town.getServerLevel();

        ImmutableList.Builder<MCHeldItem> items = ImmutableList.builder();
        if (tools.hasAxe()) {
            List<MCHeldItem> axed = computeAxedItems(town, maxItems, biome);
            items.addAll(axed);
            maxItems = maxItems - axed.size();
        } else if (tools.hasPick()) {
            List<MCHeldItem> axed = computeWoodPickaxedItems(level, maxItems);
            items.addAll(axed);
            maxItems = maxItems - axed.size();
        } else if (tools.hasShovel()) {
            List<MCHeldItem> axed = computeWoodShoveledItems(level, maxItems);
            items.addAll(axed);
            maxItems = maxItems - axed.size();
        } else if (tools.hasRod()) {
            List<MCHeldItem> axed = computeFishedItems(level, maxItems);
            items.addAll(axed);
            maxItems = maxItems - axed.size();
        }
        // TODO: Handle other tool types
        else {
            // Increase the number of gathered items if no tool is carried
            items.addAll(computeGatheredItems(level, Math.min(3, maxItems), maxItems));
        }
        items.addAll(computeGatheredItems(level, Math.min(6, maxItems), maxItems));

        ImmutableList<MCHeldItem> list = items.build();

        Questown.LOGGER.debug("[VMJ] Presenting items to gatherer: {}", list);

        return list;
    }

    @NotNull
    private static List<MCHeldItem> computeGatheredItems(
            ServerLevel level,
            int minItems,
            int maxItems
    ) {
        GathererTools.LootTablePrefix prefix = GathererTools.NO_TOOL_TABLE_PREFIX;
        ResourceLocation rl = new ResourceLocation(Questown.MODID, prefix.value());
        LootTable lootTable = level.getServer().getLootTables().get(rl);
        return getLoots(level, lootTable, minItems, maxItems, prefix, null);
    }

    @NotNull
    private static List<MCHeldItem> computeAxedItems(
            TownInterface town,
            int maxItems,
            ResourceLocation biome
    ) {
        GathererTools.LootTablePrefix prefix = GathererTools.AXE_LOOT_TABLE_PREFIX;
        String id = String.format("%s/%s/%s", prefix, biome.getNamespace(), biome.getPath());
        ResourceLocation rl = new ResourceLocation(Questown.MODID, id);
        LootTables tables = town.getServerLevel().getServer().getLootTables();
        if (!tables.getIds().contains(rl)) {
            rl = new ResourceLocation(Questown.MODID, "jobs/gatherer_axe/default");
        }
        LootTable lootTable = tables.get(rl);
        return getLoots(town.getServerLevel(), lootTable, 3, maxItems, prefix, null);
    }

    @NotNull
    private static List<MCHeldItem> computeWoodPickaxedItems(
            ServerLevel level,
            int maxItems
    ) {
        ResourceLocation rl = new ResourceLocation(Questown.MODID, "jobs/gatherer_plains_pickaxe_wood");
        LootTable lootTable = level.getServer().getLootTables().get(rl);
        GathererTools.LootTablePrefix prefix = new GathererTools.LootTablePrefix("jobs/gatherer_pickaxe");
        return getLoots(level, lootTable, 3, maxItems, prefix, null);
    }

    @NotNull
    private static List<MCHeldItem> computeWoodShoveledItems(
            ServerLevel level,
            int maxItems
    ) {
        ResourceLocation rl = new ResourceLocation(Questown.MODID, "jobs/gatherer_plains_shovel_wood");
        LootTable lootTable = level.getServer().getLootTables().get(rl);
        GathererTools.LootTablePrefix prefix = new GathererTools.LootTablePrefix("jobs/gatherer_shovel");
        return getLoots(level, lootTable, 3, maxItems, prefix, null);
    }

    @NotNull
    private static List<MCHeldItem> computeFishedItems(
            ServerLevel level,
            int maxItems
    ) {
        // TODO[ASAP]: Should held item also track the namespace of the loot table?
        ResourceLocation rl = new ResourceLocation("minecraft", "gameplay/fishing");
        LootTable lootTable = level.getServer().getLootTables().get(rl);
        GathererTools.LootTablePrefix prefix = new GathererTools.LootTablePrefix("jobs/gatherer_fishing");
        return getLoots(level, lootTable, 3, maxItems, prefix, null);
    }

    @NotNull
    private static List<MCHeldItem> getLoots(
            ServerLevel level,
            LootTable lootTable,
            int minItems,
            int maxItems,
            GathererTools.LootTablePrefix prefix,
            @Nullable ResourceLocation biome
    ) {
        if (maxItems <= 0) {
            return ImmutableList.of();
        }
        final ResourceLocation fBiome;
        if (biome == null) {
            fBiome = new ResourceLocation("forest"); // TODO: store "home biomes" on job and choose one at random
        } else {
            fBiome = biome;
        }

        LootContext.Builder lcb = new LootContext.Builder(level);
        LootContext lc = lcb.create(LootContextParamSets.EMPTY);

        ArrayList<ItemStack> rItems = new ArrayList<>();
        int max = Math.min(minItems, level.random.nextInt(maxItems) + 1);
        while (rItems.size() < max) {
            rItems.addAll(lootTable.getRandomItems(lc));
        }
        Collections.shuffle(rItems);
        int subLen = Math.min(rItems.size(), maxItems);
        List<MCHeldItem> list = rItems.stream()
                .filter(v -> !v.isEmpty())
                .map(MCTownItem::fromMCItemStack)
                .map(v -> MCHeldItem.fromLootTable(v, prefix, fBiome))
                .toList()
                .subList(0, subLen);
        return list;
    }


    private BlockPos setupForLeaveTown(TownInterface town) {
        QT.JOB_LOGGER.debug("Visitor is searching for a town gate");
        // TODO: Get the CLOSEST gate?
        return town.getEnterExitPos();
    }


    public boolean openScreen(
            ServerPlayer sp,
            VisitorMobEntity e
    ) {
        return Jobs.openInventoryAndStatusScreen(journal.getCapacity(), sp, e, ID);
    }

    public Container getInventory() {
        return inventory;
    }

    public GathererJournal.Status getStatus() {
        return journal.getStatus();
    }

    @Override
    public Function<Void, Void> addStatusListener(StatusListener l) {
        return journal.addStatusListener(l);
    }

    @Override
    public boolean isJumpingAllowed(BlockState onBlock) {
        return true;
    }

    @Override
    public void initializeItems(Iterable<MCHeldItem> mcTownItemStream) {
        journal.setItems(mcTownItemStream);
    }

    public GathererJournal.Snapshot<MCHeldItem> getJournalSnapshot() {
        return journal.getSnapshot(MCHeldItem::Air);
    }

    public void initialize(Snapshot<MCHeldItem> journal) {
        this.journal.initialize((GathererJournal.Snapshot<MCHeldItem>) journal);
    }

    @Override
    public boolean isInitialized() {
        return journal.isInitialized();
    }

    @Override
    public JobID getId() {
        return ID;
    }

    @Override
    public JobName getJobName() {
        return new JobName("jobs.gatherer");
    }

    @Override
    public boolean addToEmptySlot(MCHeldItem i) {
        return journal.addItemIfSlotAvailable(i);
    }

    public static MCTownStateWorldInteraction timeWarpWI() {
        throw new UnsupportedOperationException(); // FIXME: Phase out this job
    }
}

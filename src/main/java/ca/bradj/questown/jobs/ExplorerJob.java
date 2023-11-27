package ca.bradj.questown.jobs;

import ca.bradj.questown.QT;
import ca.bradj.questown.core.init.items.ItemsInit;
import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.integration.minecraft.MCTownItem;
import ca.bradj.questown.items.QTNBT;
import ca.bradj.questown.jobs.leaver.LeaverJob;
import ca.bradj.questown.mobs.visitor.VisitorMobEntity;
import ca.bradj.questown.town.interfaces.TownInterface;
import com.google.common.collect.ImmutableList;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Collection;
import java.util.UUID;
import java.util.function.Function;

public class ExplorerJob extends LeaverJob {

    public static final JobID ID = new JobID("gatherer", "explore");

    public ExplorerJob(
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
        return new ExplorerJournal<MCTownItem, MCHeldItem>(
                signalSource, MCHeldItem::Air,
                tsp, inventoryCapacity
        ) {
            @Override
            protected void changeStatus(GathererJournal.Status s) {
                super.changeStatus(s);
                QT.JOB_LOGGER.debug("Changed status to {}", s);
            }
        };
    }

    private static MCHeldItem mapsAreNotLoot(MCTownItem item) {
        // It's not actually "from town", but the map items produced by explorers
        // also shouldn't be considered "from a biome"
        return MCHeldItem.fromTown(item);
    }

    @Override
    public Collection<MCHeldItem> getLoot(GathererJournal.Tools tools) {
        return getLootFromLevel(town);
    }

    private static Collection<MCHeldItem> getLootFromLevel(
            TownInterface town
    ) {
        if (town == null || town.getServerLevel() == null) {
            return ImmutableList.of();
        }
        ServerLevel level = town.getServerLevel();

        ItemStack map = ItemsInit.GATHERER_MAP.get().getDefaultInstance();

        // TODO: Get from JSON files so mod can be extended
        ImmutableList<ResourceLocation> biomes = ImmutableList.of(
                new ResourceLocation("dark_forest"),
                new ResourceLocation("desert"),
                new ResourceLocation("jungle")
        );

        ResourceLocation biome = biomes.get(level.getRandom().nextInt(biomes.size()));

        QTNBT.putString(map, "biome", biome.toString());

        ImmutableList<MCHeldItem> list = ImmutableList.of(ExplorerJob.mapsAreNotLoot(MCTownItem.fromMCItemStack(map)));

        QT.JOB_LOGGER.debug("Presenting items to explorer: {}", list);

        return list;
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
    public TranslatableComponent getJobName() {
        return new TranslatableComponent("jobs.gatherer");
    }

    @Override
    public boolean addToEmptySlot(MCHeldItem i) {
        return journal.addItemIfSlotAvailable(i);
    }
}

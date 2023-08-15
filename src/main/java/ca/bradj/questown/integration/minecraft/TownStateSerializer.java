package ca.bradj.questown.integration.minecraft;

import ca.bradj.questown.Questown;
import ca.bradj.questown.core.init.BlocksInit;
import ca.bradj.questown.jobs.GathererJournal;
import ca.bradj.questown.logic.TownCycle;
import ca.bradj.questown.mobs.visitor.ContainerTarget;
import ca.bradj.questown.town.TownContainers;
import ca.bradj.questown.town.TownState;
import ca.bradj.roomrecipes.logic.interfaces.WallDetector;
import com.google.common.collect.ImmutableList;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class TownStateSerializer {

    public static final TownStateSerializer INSTANCE = new TownStateSerializer();

    public CompoundTag store(TownState<MCContainer, MCTownItem> state) {
        CompoundTag tag = new CompoundTag();
        tag.putLong("world_time_at_sleep", state.worldTimeAtSleep);
        ListTag containers = new ListTag();
        for (ContainerTarget<MCContainer, MCTownItem> c : state.containers) {
            CompoundTag cTag = new CompoundTag();
            cTag.putInt("x", c.getPosition().x);
            cTag.putInt("y", c.getYPosition());
            cTag.putInt("z", c.getPosition().z);

            ListTag items = new ListTag();
            for (MCTownItem i : c.getItems()) {
                items.add(i.serializeNBT());
            }
            cTag.put("items", items);

            containers.add(cTag);
        }
        tag.put("containers", containers);

        ListTag villagers = new ListTag();
        for (TownState.VillagerData<MCTownItem> e : state.villagers) {
            CompoundTag vTag = new CompoundTag();
            vTag.putDouble("x", e.xPosition);
            vTag.putDouble("y", e.yPosition);
            vTag.putDouble("z", e.zPosition);
            vTag.putString("journal_status", e.journal.status().name());
            ListTag journalItems = new ListTag();
            for (MCTownItem item : e.journal.items()) {
                journalItems.add(item.serializeNBT());
            }
            vTag.put("journal_items", journalItems);
            vTag.putUUID("uuid", e.uuid);
            villagers.add(vTag);
        }
        tag.put("villagers", villagers);

        return tag;
    }

    public interface GatesGetter {
        boolean isGateValid(BlockPos bp);
    }

    public TownState<MCContainer, MCTownItem> load(
            CompoundTag tag, ServerLevel level, GatesGetter gg
    ) {
        long worldTimeAtSleep = tag.getLong("world_time_at_sleep");
        ImmutableList<ContainerTarget<MCContainer, MCTownItem>> containers = loadContainers(tag, level);
        ImmutableList<TownState.VillagerData<MCTownItem>> villagers = loadVillagers(tag);
        List<BlockPos> gates = loadGates(tag, gg);
        return new TownState<>(villagers, containers, gates, worldTimeAtSleep);
    }

    private ImmutableList<BlockPos> loadGates(
            CompoundTag tag,
            GatesGetter gg
    ) {
        ImmutableList.Builder<BlockPos> b = ImmutableList.builder();
        ListTag positions = tag.getList("gate_positions", Tag.TAG_COMPOUND);
        for (Tag pTag : positions) {
            CompoundTag pcTag = (CompoundTag) pTag;
            int x = pcTag.getInt("x");
            int y = pcTag.getInt("y");
            int z = pcTag.getInt("z");
            BlockPos bp = new BlockPos(x, y, z);
            if (!gg.isGateValid(bp)) {
                Questown.LOGGER.error("Gate was in town state, but not found in world. This is a bug. {}", bp);
                continue;
            }
            b.add(bp);
        }
        return b.build();
    }

    @NotNull
    private static ImmutableList<TownState.VillagerData<MCTownItem>> loadVillagers(CompoundTag tag) {
        ImmutableList.Builder<TownState.VillagerData<MCTownItem>> b = ImmutableList.builder();
        ListTag villagers = tag.getList("villagers", Tag.TAG_COMPOUND);
        for (Tag vTag : villagers) {
            CompoundTag vcTag = (CompoundTag) vTag;
            int x = vcTag.getInt("x");
            int y = vcTag.getInt("y");
            int z = vcTag.getInt("z");
            GathererJournal.Status status = GathererJournal.Status.from(vcTag.getString("journal_status"));
            ListTag items = vcTag.getList("journal_items", Tag.TAG_COMPOUND);
            ImmutableList.Builder<MCTownItem> iB = ImmutableList.builder();
            for (Tag itemTag : items) {
                CompoundTag itemCTag = (CompoundTag) itemTag;
                iB.add(MCTownItem.of(itemCTag));
            }
            b.add(new TownState.VillagerData<>(
                    x, y, z,
                    new GathererJournal.Snapshot<>(status, iB.build()),
                    vcTag.getUUID("uuid")
            ));
        }

        return b.build();
    }

    private ImmutableList<ContainerTarget<MCContainer, MCTownItem>> loadContainers(
            CompoundTag tag,
            ServerLevel level
    ) {
        ImmutableList.Builder<ContainerTarget<MCContainer, MCTownItem>> cB = ImmutableList.builder();
        ListTag containers = tag.getList("containers", Tag.TAG_COMPOUND);
        for (Tag cTag : containers) {
            CompoundTag ccTag = (CompoundTag) cTag;

            ImmutableList.Builder<MCTownItem> cItems = ImmutableList.builder();
            ListTag items = ccTag.getList("items", Tag.TAG_COMPOUND);
            for (Tag item : items) {
                CompoundTag icTag = (CompoundTag) item;
                ItemStack stack = ItemStack.of(icTag.getCompound("item"));
                cItems.add(MCTownItem.fromMCItemStack(stack));
            }

            int x = ccTag.getInt("x");
            int y = ccTag.getInt("y");
            int z = ccTag.getInt("z");
            BlockPos pos = new BlockPos(x, y, z);
            BlockState bs = level.getBlockState(pos);
            if (!(bs.getBlock() instanceof ChestBlock)) {
                Questown.LOGGER.error(
                        "There used to be a chest at {}, but now there isn't. " +
                                "This is a bug and will cause items to be lost.", pos
                );
                continue;
            }
            ContainerTarget<MCContainer, MCTownItem> ct = TownContainers.fromChestBlock(
                    pos, (ChestBlock) bs.getBlock(), level
            );
            cB.add(ct);
            ImmutableList<MCTownItem> stateItems = cItems.build();
            checkItems(ct, stateItems);
        }
        return cB.build();
    }

    private void checkItems(
            ContainerTarget ct,
            ImmutableList<MCTownItem> stateItems
    ) {
        ImmutableList<MCTownItem> containerItems = ct.getItems();
        int cSize = containerItems.size();
        int sSize = stateItems.size();
        if (cSize != sSize) {
            Questown.LOGGER.error("Container items do not match stored state. This is a bug and may cause items to be lost [{}, {}]", cSize, sSize);
        }
        for (int i = 0; i < cSize; i++) {
            MCTownItem cItem = containerItems.get(i);
            MCTownItem sItem = stateItems.get(i);
            if (!cItem.equals(sItem)) {
                Questown.LOGGER.error(
                        "In slot {}, expected {} but state had {}. This is a bug and may cause items to be lost",
                        i, cItem, sItem
                );
            }
        }
    }

}

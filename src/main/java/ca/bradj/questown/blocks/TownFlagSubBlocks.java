package ca.bradj.questown.blocks;

import ca.bradj.questown.QT;
import ca.bradj.questown.core.Config;
import ca.bradj.questown.mc.Compat;
import com.google.common.collect.ImmutableMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Stack;
import java.util.function.Function;

public final class TownFlagSubBlocks {

    private final Stack<BlockPos> pending = new Stack<>();
    private final Map<BlockPos, Integer> pendingTicks = new HashMap<>();
    private final Map<BlockPos, Integer> ticksWithoutParent = new HashMap<>();
    private final Map<BlockPos, Integer> ticksWithoutChild = new HashMap<>();
    private final Map<BlockPos, Function<BlockPos, Collection<ItemStack>>> dropOnOrphaned = new HashMap<>();
    private final BlockPos flagPos;
    private boolean parentIsUnloaded = false;

    public TownFlagSubBlocks(BlockPos blockPos) {
        this.flagPos = blockPos;
    }

    public void parentTick(ServerLevel sl) {
        parentIsUnloaded = false;
        if (!pending.isEmpty()) {
            BlockPos popped = pending.pop();
            try {
                if (!this.initialize(sl, popped)) {
                    pending.push(popped); // TODO: Make this a queue?
                }
            } catch (IllegalStateException e) {
                QT.FLAG_LOGGER.error("Giving up on detecting sub block at {}: {}", popped, e);
            }
        }

        ImmutableMap.Builder<BlockPos, Integer> allZeros = ImmutableMap.builder();
        ticksWithoutParent.forEach((k, b) -> allZeros.put(k, 0));
        ticksWithoutParent.putAll(allZeros.build());

        ImmutableMap.Builder<BlockPos, Integer> twoc = ImmutableMap.builder();
        ticksWithoutChild.forEach((bp, v) -> {
            if (v > Compat.configGet(Config.FLAG_SUB_BLOCK_REMOVED_TICKS).get()) {
                dropDrops(sl, bp, dropOnOrphaned.get(bp).apply(flagPos));
                ticksWithoutParent.remove(bp);
                return;
            }
            twoc.put(bp, v + 1);
        });
        ticksWithoutChild.clear();
        ticksWithoutChild.putAll(twoc.build());
    }

    public void tick(
            ServerLevel sl,
            BlockPos pos
    ) {
        if (this.parentIsUnloaded) {
            return;
        }

        ticksWithoutChild.put(pos, 0);
        Integer twop = ticksWithoutParent.get(pos);
        if (twop == null) {
            return;
        }
        ticksWithoutParent.put(pos, twop + 1);
        if (twop >= Compat.configGet(Config.FLAG_SUB_BLOCK_RETENTION_TICKS).get()) {
            QT.BLOCK_LOGGER.debug("Parent has stopped ticking. Entity removed at {}", pos);
            sl.removeBlock(pos, true);
            dropDrops(sl, pos, dropOnOrphaned.get(pos).apply(flagPos));
        }
    }

    private static void dropDrops(
            ServerLevel sl,
            BlockPos pos,
            Collection<ItemStack> drops
    ) {
        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();
        // TODO: Add "paper bag" block containing dropped item so items aren't eventually lost
        drops.forEach(i -> sl.addFreshEntity(new ItemEntity(sl, x, y, z, i)));
    }

    private boolean initialize(
            ServerLevel sl,
            BlockPos pop
    ) {
        BlockEntity e = sl.getBlockEntity(pop);
        if (e instanceof TownFlagSubEntity s) {
            ticksWithoutParent.put(pop, 0);
            dropOnOrphaned.put(pop, s::dropWhenOrphaned);
            s.addTickListener(() -> this.tick(sl, pop));
            QT.FLAG_LOGGER.debug("Registered sub block of town flag: {}", s);
            return true;
        }
        Integer newVal = this.pendingTicks.compute(pop, TownFlagSubBlocks::increment);
        if (newVal > Compat.configGet(Config.FLAG_SUB_BLOCK_DETECTION_TICKS).get()) {
            throw new IllegalStateException("Could not register sub block because wrong entity type: " + e);
        }
        return false;
    }

    @NotNull
    private static Integer increment(
            BlockPos p,
            Integer v
    ) {
        return v == null ? 1 : v + 1;
    }

    public void register(BlockPos matPos) {
        this.pending.push(matPos);
    }

    public void parentUnloaded() {
        this.parentIsUnloaded = true;
    }
}

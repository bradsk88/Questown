package ca.bradj.questown.commands.test;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Wires {@link ContainerFlowCounter} to live containers at the insert/remove
 * seam. A vanilla {@link Container} exposes no change callback, so the tracker
 * snapshots each tracked container's slots at a fixed cadence and diffs the
 * totals: a slot gain is recorded as produced, a slot loss as consumed. The
 * diff is net-per-slot, so a swap (two items moving between slots) nets to
 * zero and is not miscounted as a flow.
 *
 * <p>Dev-only scenario plumbing, not a mod feature: it reads
 * {@code BlockEntity.getContainer()} and never writes to the world.
 */
public final class ContainerFlowTracker {

    private final ContainerFlowCounter counter = new ContainerFlowCounter();
    private final Set<BlockPos> containers = new java.util.LinkedHashSet<>();
    /** item -> total count, per container pos, from the last poll. */
    private final Map<BlockPos, Map<String, Integer>> previous = new HashMap<>();
    private final Map<String, Integer> stock = new HashMap<>();
    private long day;

    public void track(ServerLevel level, BlockPos pos) {
        containers.add(pos.immutable());
        if (level != null && level.getGameTime() > 0) {
            syncDay(level);
        }
    }

    private void syncDay(ServerLevel level) {
        // Game day number; vanilla does not advance it within the tick cycle
        // unless the day counter rolls, which a held realtime watch may cross.
        long d = level.getDayTime() >= 24000L
                ? level.getDayTime() / 24000L
                : 0L;
        this.day = d;
    }

    /**
     * Polls every tracked container and records net changes against the last
     * poll. Call from the watch loop on a fixed cadence (e.g. every 200
     * ticks, alongside the room builder).
     */
    public void poll(ServerLevel level) {
        for (BlockPos pos : containers) {
            BlockEntity be = level.getBlockEntity(pos);
            if (!(be instanceof Container c)) {
                continue;
            }
            Map<String, Integer> now = countStock(c);
            Map<String, Integer> prev = previous.getOrDefault(pos, Map.of());
            for (String item : union(now.keySet(), prev.keySet())) {
                int nowCount = now.getOrDefault(item, 0);
                int prevCount = prev.getOrDefault(item, 0);
                int delta = nowCount - prevCount;
                if (delta > 0) {
                    counter.recordInsert(item, day, delta);
                } else if (delta < 0) {
                    counter.recordExtract(item, day, -delta);
                }
            }
            previous.put(pos, now);
        }
        // Refresh the stock snapshot from all tracked containers.
        stock.clear();
        for (Map<String, Integer> per : previous.values()) {
            for (Map.Entry<String, Integer> e : per.entrySet()) {
                ContainerFlowCounter.putStock(stock, e.getKey(), e.getValue());
            }
        }
        counter.takeStockSnapshot(stock);
    }

    public ContainerFlowCounter counter() {
        return counter;
    }

    /** Total per-item count across one container. */
    public static Map<String, Integer> countStock(Container c) {
        Map<String, Integer> out = new HashMap<>();
        for (int i = 0; i < c.getContainerSize(); i++) {
            ItemStack s = c.getItem(i);
            if (s.isEmpty()) {
                continue;
            }
            out.merge(s.toString(), s.getCount(), Integer::sum);
        }
        return out;
    }

    private static Set<String> union(Set<String> a, Set<String> b) {
        Set<String> out = new java.util.HashSet<>(a);
        out.addAll(b);
        return out;
    }
}

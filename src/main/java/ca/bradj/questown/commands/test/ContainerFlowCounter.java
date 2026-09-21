package ca.bradj.questown.commands.test;

import java.util.HashMap;
import java.util.Map;

/**
 * Counts per-item container flow for a self-driving watch scenario:
 * produced (items entering a tracked container), consumed (items leaving),
 * and a point-in-time stock snapshot. Pure bookkeeping, no Minecraft types,
 * so it is unit-testable without a Forge load.
 *
 * <p>A "flow" is a single insert or extract call into/out of one tracked
 * container; the item is identified by its {@code ItemStack} string (id plus
 * any component data, so renamed/charged stacks are not conflated).
 *
 * <p>Day buckets use the level's game day number, which the vanilla tick
 * cycle does not advance, so a realtime watch keeps producing day-0 buckets
 * by design: the counter reports what happened, not what the town thinks
 * its day number is. The daily report is expected to key its deltas on
 * wall-clock watch windows instead.
 */
public final class ContainerFlowCounter {

    private final Map<String, Map<Long, Long>> produced = new HashMap<>();
    private final Map<String, Map<Long, Long>> consumed = new HashMap<>();
    /** Last stock snapshot taken, item -> count. Refreshed by {@link #takeStockSnapshot}. */
    private Map<String, Integer> lastStock = new HashMap<>();

    /** Records {@code count} copies of the item entering a tracked container. */
    public void recordInsert(String itemKey, long day, int count) {
        if (count <= 0) {
            return;
        }
        produced.computeIfAbsent(itemKey, k -> new HashMap<>())
                .merge(day, (long) count, Long::sum);
    }

    /** Records {@code count} copies of the item leaving a tracked container. */
    public void recordExtract(String itemKey, long day, int count) {
        if (count <= 0) {
            return;
        }
        consumed.computeIfAbsent(itemKey, k -> new HashMap<>())
                .merge(day, (long) count, Long::sum);
    }

    /** Replaces the last stock snapshot. Call this on a fixed cadence. */
    public void takeStockSnapshot(Map<String, Integer> stock) {
        this.lastStock = new HashMap<>(stock);
    }

    public Map<String, Map<Long, Long>> produced() {
        return produced;
    }

    public Map<String, Map<Long, Long>> consumed() {
        return consumed;
    }

    public Map<String, Integer> lastStock() {
        return lastStock;
    }

    public int stockOf(String itemKey) {
        return lastStock.getOrDefault(itemKey, 0);
    }

    /** Total produced across all days for one item. */
    public long totalProduced(String itemKey) {
        return produced.getOrDefault(itemKey, Map.of()).values().stream().mapToLong(Long::longValue).sum();
    }

    /** Total consumed across all days for one item. */
    public long totalConsumed(String itemKey) {
        return consumed.getOrDefault(itemKey, Map.of()).values().stream().mapToLong(Long::longValue).sum();
    }

    /**
     * The six-category daily report line for one item: produced and consumed
     * per day, plus the stock snapshot. Deterministic order: sorted items,
     * ascending days. Empty counter yields an empty list.
     */
    public java.util.List<String> reportLines() {
        java.util.List<String> out = new java.util.ArrayList<>();
        java.util.TreeMap<String, String> byItem = new java.util.TreeMap<>();
        for (String item : unionKeys()) {
            StringBuilder sb = new StringBuilder("[village] flow ");
            sb.append(item)
                    .append(" produced=").append(dayTotals(produced, item))
                    .append(" consumed=").append(dayTotals(consumed, item))
                    .append(" stock=").append(stockOf(item));
            byItem.put(item, sb.toString());
        }
        out.addAll(byItem.values());
        return out;
    }

    private java.util.Set<String> unionKeys() {
        java.util.Set<String> keys = new java.util.HashSet<>();
        keys.addAll(produced.keySet());
        keys.addAll(consumed.keySet());
        keys.addAll(lastStock.keySet());
        return keys;
    }

    /** Format a day -> total map as "dayN:n" pairs, ascending day. */
    private static String dayTotals(Map<String, Map<Long, Long>> table, String item) {
        Map<Long, Long> days = new java.util.TreeMap<>(table.getOrDefault(item, Map.of()));
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Map.Entry<Long, Long> e : days.entrySet()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append('d').append(e.getKey()).append(':').append(e.getValue());
        }
        return sb.toString();
    }

    /**
     * Stock of one item in one container, as a single-snapshot map entry.
     * Convenience for building the input to {@link #takeStockSnapshot}:
     * {@code countStock(container)} per tracked container, merged by the caller.
     */
    public static void putStock(Map<String, Integer> stock, String itemKey, int count) {
        if (count <= 0) {
            return;
        }
        stock.merge(itemKey, count, Integer::sum);
    }
}

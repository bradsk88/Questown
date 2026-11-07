package ca.bradj.questown.town.needs;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * The town's unmet needs, by villager and item. A need keeps its first-seen time across passes; a
 * villager blocked on several items has them all; its bubble names the oldest. {@link #rank()} groups
 * the needs a quest can help with by item, most villagers first.
 */
public final class UnmetNeedTracker {

    private record Key(UUID townie, String root, String key) {}

    private final Map<Key, Row> rows = new HashMap<>();

    /** One villager waiting on one item. */
    public record Row(
            UUID townie,
            String root,
            String key,
            GateReason reason,
            long since,
            int attempts
    ) {
    }

    /** One item: how many villagers wait on it, and since when. */
    public record Need(
            String key,
            int townies,
            long since
    ) {
    }

    /** A villager and item seen for the first time this pass. */
    public record FirstEnter(
            UUID townie,
            String key,
            GateReason reason
    ) {
    }

    /** Registers one villager's need. @return true the first time only */
    public boolean enter(
            long tick,
            String root,
            UUID townie,
            String key,
            GateReason reason
    ) {
        Key k = new Key(townie, root, key);
        Row existing = rows.get(k);
        if (existing != null) {
            rows.put(k, new Row(existing.townie(), existing.root(), existing.key(), reason, existing.since(), existing.attempts() + 1));
            return false;
        }
        // A new item adds to the villager's needs rather than replacing them.
        rows.put(k, new Row(townie, root, key, reason, tick, 1));
        return true;
    }

    /** One pass for a root: adds the live needs and clears the ones no longer live. @return the needs seen for the first time */
    public List<FirstEnter> applyPass(
            long tick,
            String root,
            List<UUID> townies,
            Map<String, GateReason> liveKeys
    ) {
        List<FirstEnter> first = new ArrayList<>();
        for (UUID townie : townies) {
            for (Map.Entry<String, GateReason> e : liveKeys.entrySet()) {
                if (enter(tick, root, townie, e.getKey(), e.getValue())) {
                    first.add(new FirstEnter(townie, e.getKey(), e.getValue()));
                }
            }
        }
        Set<String> live = liveKeys.keySet();
        for (Row r : new ArrayList<>(rows.values())) {
            if (r.root().equals(root) && !live.contains(r.key())) {
                exitKey(root, r.key());
            }
        }
        return first;
    }

    /** Clears every villager's need for this item under the root. */
    public void exitKey(String root, @Nullable String key) {
        rows.values().removeIf(r -> r.root().equals(root) && (r.key() == null ? key == null : r.key().equals(key)));
    }

    /** The villager's hint: its oldest live named need under the root, or null. */
    public @Nullable String primaryKey(String root, UUID townie) {
        return primaryKey(root, townie, k -> true);
    }

    /** As above, only for items {@code canHold} accepts: jobs this villager can do. */
    public @Nullable String primaryKey(String root, UUID townie, Predicate<String> canHold) {
        String best = null;
        long bestSince = Long.MAX_VALUE;
        for (Row r : rows.values()) {
            if (!r.root().equals(root) || !r.townie().equals(townie) || r.key() == null) {
                continue;
            }
            if (!r.reason().supplyItem()) {
                continue;
            }
            if (!canHold.test(r.key())) {
                continue;
            }
            if (r.since() < bestSince || (r.since() == bestSince && best != null && r.key().compareTo(best) < 0)) {
                best = r.key();
                bestSince = r.since();
            }
        }
        return best;
    }

    /** The room the villager's oldest missing-room need asks for, or null. */
    public @Nullable String primaryRoom(String root, UUID townie, Predicate<String> canHold) {
        String best = null;
        long bestSince = Long.MAX_VALUE;
        for (Row r : rows.values()) {
            if (!r.root().equals(root) || !r.townie().equals(townie) || r.key() == null) {
                continue;
            }
            if (!(r.reason() instanceof GateReason.NoRoom nr) || nr.room() == null || !canHold.test(r.key())) {
                continue;
            }
            if (r.since() < bestSince || (r.since() == bestSince && best != null && nr.room().compareTo(best) < 0)) {
                best = nr.room();
                bestSince = r.since();
            }
        }
        return best;
    }

    /** Clears every need under a root; the town found work there again. */
    public void exitRoot(String root) {
        rows.values().removeIf(r -> r.root().equals(root));
    }

    public List<Row> rows() {
        List<Row> all = new ArrayList<>(rows.values());
        all.sort(Comparator.comparing(Row::root)
                .thenComparing(Row::townie)
                .thenComparing(Row::key, Comparator.nullsFirst(Comparator.naturalOrder())));
        return all;
    }

    /** The needs a quest can help with, by item: most villagers first, then oldest. Needs with no item are left out. */
    public List<Need> rank() {
        Map<String, List<Row>> byKey = new HashMap<>();
        for (Row r : rows.values()) {
            if (!r.reason().actionable() || r.key() == null) {
                continue;
            }
            byKey.computeIfAbsent(r.key(), x -> new ArrayList<>()).add(r);
        }
        List<Need> out = new ArrayList<>();
        for (Map.Entry<String, List<Row>> e : byKey.entrySet()) {
            long since = e.getValue().stream().mapToLong(Row::since).min().orElse(e.getValue().get(0).since());
            out.add(new Need(e.getKey(), e.getValue().size(), since));
        }
        out.sort(Comparator.comparing(Need::townies).reversed()
                .thenComparing(Comparator.comparingLong(Need::since))
                .thenComparing(Need::key));
        return out;
    }
}

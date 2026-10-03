package ca.bradj.questown.town.needs;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Enter/exit bookkeeping for a town's unmet needs (design:
 * {@code .scratch/gannet-top-need/unmet-need-design.md}, items 3, 4 and 6).
 *
 * <p>A registration is idempotent per (townie, item key): entering the same
 * block again on a later recompute pass is the same need, so {@code since}
 * stays at first-enter and only the per-pass {@code attempts} diagnostic
 * grows. Rows are bounded by the roster and are cleared the moment the
 * town's recompute finds viable work again — no re-add-every-pass, no
 * cross-day phantom growth (the run-2 {@code swords=61} failure mode that
 * the old random-label + re-add path produced).
 *
 * <p>{@link #rank()} is the batch's input: actionable needs only
 * (NO_ROOM and key-carrying BLOCKED; NO_REQUEST is a board problem, not a
 * supply one), grouped by the supply key, then townies-desc, then
 * earliest-enter — determined by the world state alone.
 */
public final class UnmetNeedTracker {

    private record Key(UUID townie, String root, String key) {}

    /** The per-pass diagnostic: how many recompute passes the need re-entered. */
    private final Map<Key, Row> rows = new HashMap<>();

    /** One blocked townie on one supply key. */
    public record Row(
            UUID townie,
            String root,
            String key,
            GateReason reason,
            long since,
            int attempts
    ) {
    }

    /** One ranked supply key: how many townies wait on it and since when. */
    public record Need(
            String key,
            int townies,
            long since
    ) {
    }

    /**
     * Register (or re-enter) one townie's need for a key.
     *
     * @return true only when this is a first entry — the caller should count
     *         the need exactly once (econ diagnostic, townie bubble), not once
     *         per recompute pass
     */
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
        // A new key for the same townie+root replaces the old one: the best-scoring
        // job — hence the key it blocks on — is the town's current statement of
        // what it waits for, and a stale key from an earlier pass would make the
        // resulting rows depend on engine timing, not world state.
        rows.values().removeIf(r -> r.townie().equals(townie) && r.root().equals(root));
        rows.put(k, new Row(townie, root, key, reason, tick, 1));
        return true;
    }

    /** Clear every need under a root — the town found viable work there again. */
    public void exitRoot(String root) {
        rows.values().removeIf(r -> r.root().equals(root));
    }

    /** Snapshot of every live registration (deterministic order for downstream reads). */
    public List<Row> rows() {
        List<Row> all = new ArrayList<>(rows.values());
        all.sort(Comparator.comparing(Row::root)
                .thenComparing(Row::townie)
                .thenComparing(Row::key, Comparator.nullsFirst(Comparator.naturalOrder())));
        return all;
    }

    /**
     * The batch's input: actionable needs grouped by supply key, ranked
     * townies-desc, then earliest-enter, then key for a stable tie. Keyless
     * rows (a NO_REQUEST diagnostic, or a NO_ROOM whose product has no
     * nameable supply) cannot be looked up against producers, so they rank
     * out.
     */
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

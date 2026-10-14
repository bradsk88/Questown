package ca.bradj.questown.town.needs;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
 * <p><b>Union semantics (2026-10-15 ruling):</b> a townie's live needs for a
 * root are a UNION, not the best-scoring job's single key. A root can be
 * blocked on several supplies at once (the cook on coal AND raw food), and
 * the player-facing hint must name all of them instead of flapping with the
 * pass's argmax. A new key joins the townie's live set
 * ({@link #enter}), a key exits when it drops out of the pass's live set
 * ({@link #applyPass}/{@link #exitKey}), and the bubble's single item is the
 * townie's oldest live key ({@link #primaryKey}) — stable across passes.
 * This is job-set-agnostic on purpose (the job independence principle,
 * CONTEXT.md): no ordering or priority between specific jobs.
 *
 * <p>{@link #rank()} is the batch's input: actionable needs only
 * (NO_ROOM and key-carrying BLOCKED; NO_REQUEST is a board problem, not a
 * supply one), grouped by the supply key, then townies-desc, then
 * earliest-enter — determined by the world state alone. A townie blocked on
 * two keys counts in both groups.
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

    /** A (townie, key) first registered this pass — the caller's hook for the first-enter side effects. */
    public record FirstEnter(
            UUID townie,
            String key,
            GateReason reason
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
        // Union semantics (2026-10-15 ruling): a new key for the same
        // townie+root JOINS the townie's live needs. The old replace-on-new-key
        // made the tracker's statement depend on which job best-scored that
        // pass (the cook's hint flapped between coal / raw food / nothing),
        // not on the world state.
        rows.put(k, new Row(townie, root, key, reason, tick, 1));
        return true;
    }

    /**
     * One recompute pass for a root: enter the root's live named keys for
     * every townie and exit the registered keys that are no longer live. The
     * caller evaluates the gate of each candidate job and hands over the
     * distinct named keys (job-set-agnostic — no ordering between jobs).
     *
     * @return the (townie, key) pairs first entered this pass, so the caller
     *         fires the first-enter side effects (econ record, bubble) once
     */
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

    /**
     * Clear every need for this root+key (all townies) — the town no longer
     * blocks on this key. A null key exits the keyless diagnostic rows.
     */
    public void exitKey(String root, @Nullable String key) {
        rows.values().removeIf(r -> r.root().equals(root) && (r.key() == null ? key == null : r.key().equals(key)));
    }

    /**
     * The townie's player-facing hint: the oldest live NAMED key at the root
     * (minimum {@code since}, ties broken key-ascending). Null when the
     * townie holds no named live key — keyless diagnostic rows never surface.
     * Deterministic given the world state: a new pass can only change it when
     * a key's liveness or age does.
     */
    public @Nullable String primaryKey(String root, UUID townie) {
        String best = null;
        long bestSince = Long.MAX_VALUE;
        for (Row r : rows.values()) {
            if (!r.root().equals(root) || !r.townie().equals(townie) || r.key() == null) {
                continue;
            }
            if (r.since() < bestSince || (r.since() == bestSince && best != null && r.key().compareTo(best) < 0)) {
                best = r.key();
                bestSince = r.since();
            }
        }
        return best;
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

status: needs-triage
date: 2026-10-03
owner: bradj (design); agent (doc)

# Unmet-need registration — design (locked)

Replaces the `TownPossibleWork.registerUnmetNed` random-label +
re-add-every-pass mechanism (run evidence: run2 `swords=61` for one idle
hunter; sword labelled onto non-sword failures; count unbounded/decay-less).

**Decision context:** the goal is a precise, predictable "what does the town
need" that (a) drives the "top non-food need → unlock producer + build base
room" batch and (b) is legible to the player. Attempts-vs-townies (D1):
**both, separate** — townies is the rank key, attempts is a diagnostic.
`NO_REQUEST` (D2): its own class, excluded from the batch.

## Design

1. **Typed gate reason per job (pure — unit-testable, Forge-free seam).**
   Formalize what `getHighestPossibleState` + `getRequestStatus` already
   compute:
   - `NO_ROOM(room)` — no job site built (batch: build-room / unlock beat)
   - `BLOCKED(state_i, missing_ingredients, missing_tools)` — first failing
     step, state-ordered (batch: producer for the missing item)
   - `NO_REQUEST` — inputs fine, product not on the board (NOT a supply need)
   - `OK`
   Inputs to the classifier: job checks (data), container predicates, request
   list — no registry in path → testable per the registry-bound-untestable
   doctrine.

2. **Evidence: two sources, townie-committed wins.**
   - In-state: a running job stalled at step i (step checks failing, step
     not advancing) — highest priority; the townie is *committed*.
   - Selection: root has no viable work → use the **best-scoring** job's
     reason (not a random one — the current bug), as the root-level signal.

3. **Registration = enter/exit, idempotent, per (townie, item-key).**
   - Register on entering a block (record `since` = first-enter tick).
   - Clear the moment the check passes (existing exit path; already exists
     in `clearUnmetItemNeeds` shape).
   - Attempts counter (the existing cumulative econ counter) becomes a
     *per-(townie,item)* diagnostic — re-purposed, not deleted.

4. **Rank (what the batch acts on), over actionable needs:**
   - Class 1 (actionable): `NO_ROOM(room)` → producer root + build room;
     `BLOCKED(…)` → producer root for the missing item.
   - Excluded: `NO_REQUEST` (a request/board problem, not a supply one).
   - Order: **townies-desc, then since-asc.** Attempts is not a rank key —
     it's noise-to-signal (retry frequency), and "old + few people" is
     already captured by `since`.

5. **Player-facing line (the understandability target):**
   `coal ×3 — waiting since 14:02 · miner is the answer (build the mineshaft)`
   item, count, age, and the fix (producer + room) — the fix text is exactly
   the producerRootsForItem + room lookup the batch already computes.
   Attempts: agent-facing only (daily report, workscore). Not shown to the player.

6. **Predictability guarantees (testable):**
   - A registration's key depends only on (townie, job, step, missing-set);
     not on retry frequency, engine timing, or which recompute pass fired.
   - Bounded by roster; decays (clears) when unblocked; no cross-day phantom
     growth (the run2 `swords=61` failure mode).
   - Given a world state, the resulting need rows are determined —
     unit-testable through the pure classifier + a small state-machine
     (register/clear) test.

## Open (not blocking the design)

- Whether `NO_REQUEST` should ever surface as a *soft* signal (a hint that
  the board/quest is empty) — player-facing, low priority, separate from
  the batch.
- In-state stall timeout: a bounded M-tick threshold for "stalled at step i"
  (so a legitimately slow step isn't flagged) — pick M from the job's own
  `time` state if present, else a default.

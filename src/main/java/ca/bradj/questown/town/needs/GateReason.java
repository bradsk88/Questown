package ca.bradj.questown.town.needs;

import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Why a job cannot run right now, as a type (design:
 * {@code .scratch/gannet-top-need/unmet-need-design.md}).
 *
 * <p>Pure over labels and booleans: the town side (registry-bound job data,
 * container scans, the request board) supplies the inputs here; the decision
 * — which class wins, and which supply item an actionable need names — is
 * what this type owns, so it is unit-testable without a Forge world.
 *
 * <p>Precedence mirrors the scoring path a blocked townie actually sees:
 * no job site built at all, then the first state whose inputs are missing,
 * then "inputs fine but nothing on the board asks for the product".
 */
public sealed interface GateReason permits
        GateReason.NoRoom,
        GateReason.Blocked,
        GateReason.NoRequest,
        GateReason.Ok {

    /**
     * The batch can build toward this: a room to place, or a producer to
     * unlock/build for the named item. {@link NoRequest} and {@link Ok} are
     * board problems, not supply ones — the batch leaves them alone.
     */
    boolean actionable();

    /**
     * The supply item this need names (a tag/item id the town's producers
     * are indexed on), or null when the need has no nameable supply.
     */
    @Nullable String itemKey();

    /**
     * Whether this is a *supply-item* need — one the town writes to its ITEM store
     * and surfaces as a {@code UNMET_ITEM} bubble. Only a {@link Blocked} on a
     * missing ingredient or tool names a supply item. A {@link NoRoom} is a job-site
     * gap (routed to the room store and the work-seeker status, not the item store);
     * a {@link NoRequest} is a board problem. Note: {@link #itemKey()} may still
     * carry the job's product (the tracker's producer handle) while {@code supplyItem()}
     * is false — that is the NoRoom case the item store must not treat as a supply need.
     */
    default boolean supplyItem() {
        if (this instanceof Blocked b) {
            return b.itemKey() != null;
        }
        return false;
    }

    /** Classify a job's gate state. A missing state has index {@code -1}. */
    static GateReason classify(
            boolean anyStateHasRoom,
            @Nullable String roomKey,
            int firstBlockedState,
            List<String> missingIngredients,
            List<String> missingTools,
            boolean productRequested,
            @Nullable String productKey
    ) {
        if (!anyStateHasRoom) {
            return new NoRoom(roomKey, productKey);
        }
        if (firstBlockedState >= 0) {
            List<String> ingredients = missingIngredients == null ? List.of() : missingIngredients;
            List<String> tools = missingTools == null ? List.of() : missingTools;
            return new Blocked(firstBlockedState, ingredients, tools);
        }
        return productRequested ? new Ok() : new NoRequest();
    }

    /**
     * The town lacks this job's room (or an ancestor's). The fix is
     * self-contained — unlock this producer and place its room — even when
     * the product itself has no nameable supply.
     */
    record NoRoom(@Nullable String room, @Nullable String supplyKey) implements GateReason {
        @Override
        public boolean actionable() {
            return true;
        }

        @Override
        public @Nullable String itemKey() {
            return supplyKey;
        }
    }

    /**
     * First failing state of the job. The key is the first missing
     * ingredient, falling back to the first missing tool — the same
     * item a producer is indexed on, so the batch can look up "who makes
     * this" without re-deriving the job's inputs.
     */
    record Blocked(
            int state,
            List<String> missingIngredients,
            List<String> missingTools
    ) implements GateReason {
        @Override
        public boolean actionable() {
            return itemKey() != null;
        }

        @Override
        public @Nullable String itemKey() {
            if (!missingIngredients.isEmpty()) {
                return missingIngredients.get(0);
            }
            if (!missingTools.isEmpty()) {
                return missingTools.get(0);
            }
            return null;
        }
    }

    /** Inputs are all fine but the board does not ask for this product. Not a supply need. */
    record NoRequest() implements GateReason {
        @Override
        public boolean actionable() {
            return false;
        }

        @Override
        public @Nullable String itemKey() {
            return null;
        }
    }

    /** Room built, inputs present, product requested. Nothing missing. */
    record Ok() implements GateReason {
        @Override
        public boolean actionable() {
            return false;
        }

        @Override
        public @Nullable String itemKey() {
            return null;
        }
    }
}

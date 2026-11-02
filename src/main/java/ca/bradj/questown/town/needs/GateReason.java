package ca.bradj.questown.town.needs;

import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Why a job can't run right now: no job site, a state missing its inputs, or nothing on the board asking for its product. */
public sealed interface GateReason permits
        GateReason.NoRoom,
        GateReason.Blocked,
        GateReason.NoRequest,
        GateReason.Ok {

    /** Whether a quest batch can help: a room to build, or a producer to unlock for the item. */
    boolean actionable();

    /** The item this need names, or null. */
    @Nullable String itemKey();

    /** Whether this is a missing ingredient or tool, which shows as an unmet-item bubble. A missing room is not. */
    default boolean supplyItem() {
        if (this instanceof Blocked b) {
            return b.itemKey() != null;
        }
        return false;
    }

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

    /** The town lacks this job's room, or an ancestor's. */
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

    /** The first state that can't run, keyed by its first missing ingredient, else its first missing tool. */
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

    /** Everything is ready but the board doesn't ask for the product. */
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

    /** Nothing missing. */
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

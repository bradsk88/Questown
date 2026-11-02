package ca.bradj.questown.mobs.helperchicken;

import java.util.Set;

/** The helper chicken's lessons, as a state machine. A lesson whose condition is already met completes the tick the lessons before it do. */
public final class ChickenArcTransitions {

    /** Everything the lessons look at. The same snapshot always gives the same next lesson. */
    public record Observed(
            boolean playerHasWand,
            boolean campfireLit,
            boolean sleepHappenedToday,
            boolean wallBlockPlaced,
            boolean doorPlaced,
            boolean roomRegistered,
            boolean roomBuilt,
            boolean signPlacedAsJobBoard,
            boolean chestPlaced,
            boolean welcomeMatPlaced,
            boolean bonusFoodGiven,
            boolean playerOpenedVillagerUi,
            boolean playerOpenedFlagUi,
            boolean worldlySeedsInAnyContainer,
            boolean playerGaveSeedsToChicken,
            boolean handPlaced
    ) {
    }

    private ChickenArcTransitions() {
    }

    /** Lessons the player may skip: they complete when the next lesson's condition is met. */
    private static final Set<ChickenBeatState> OPTIONAL =
            Set.of(ChickenBeatState.WAITING_FOR_FREE_FOOD);

    /** The next lesson: past every one whose condition is met. Complete and forfeit never change. */
    public static ChickenBeatState advance(
            ChickenBeatState current,
            Observed o
    ) {
        if (current == ChickenBeatState.COMPLETE || current == ChickenBeatState.FORFEIT) {
            return current;
        }
        ChickenBeatState state = current;
        while (shouldAdvance(state, o)) {
            ChickenBeatState next = nextOrdinal(state);
            if (next == state) {
                return state;
            }
            state = next;
        }
        return state;
    }

    private static boolean shouldAdvance(
            ChickenBeatState state,
            Observed o
    ) {
        if (isSatisfied(state, o)) {
            return true;
        }
        return OPTIONAL.contains(state) && isSatisfied(nextOrdinal(state), o);
    }

    public static boolean isSatisfied(
            ChickenBeatState state,
            Observed o
    ) {
        return switch (state) {
            case WAITING_FOR_STICK -> o.playerHasWand();
            case WAITING_FOR_WAND_ON_CAMPFIRE -> o.campfireLit();
            case SUNSET_AND_MAP -> o.sleepHappenedToday();
            // A hand-placed flag has no hole to fill, so skip that lesson.
            case WAITING_FOR_WALL_BLOCK -> o.wallBlockPlaced() || o.handPlaced();
            case WAITING_FOR_DOOR -> o.doorPlaced();
            case WAITING_FOR_WAND_ON_DOOR -> o.roomRegistered();
            case WAITING_FOR_ROOM_BUILD -> o.roomBuilt();
            case WAITING_FOR_SIGN -> o.signPlacedAsJobBoard();
            case WAITING_FOR_CHEST -> o.chestPlaced();
            case WAITING_FOR_PRESSURE_PLATE -> o.welcomeMatPlaced();
            case WAITING_FOR_FREE_FOOD -> o.bonusFoodGiven();
            case WAITING_FOR_VILLAGER_UI -> o.playerOpenedVillagerUi();
            case WAITING_FOR_FLAG_UI -> o.playerOpenedFlagUi();
            case AWAITING_WORLDLY_SEEDS_DELIVERY -> o.playerGaveSeedsToChicken();
            case COMPLETE, FORFEIT -> false;
        };
    }

    private static ChickenBeatState nextOrdinal(ChickenBeatState s) {
        ChickenBeatState[] all = ChickenBeatState.values();
        int i = s.ordinal();
        if (i + 1 >= all.length) {
            return s;
        }
        ChickenBeatState next = all[i + 1];
        // Forfeit only comes from the remove command.
        return next == ChickenBeatState.FORFEIT ? s : next;
    }
}

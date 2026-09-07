package ca.bradj.questown.commands;

import ca.bradj.questown.mobs.helperchicken.ChickenBeatState;
import org.jetbrains.annotations.Nullable;

/**
 * Agent-facing guidance for the helper-chicken onboarding arc, surfaced by
 * {@code /_qtdev help}.
 *
 * <p>The helper chicken shows a player a bubble icon and a right-click hint
 * (see {@code ChickenArcPresentation}). An agent driving a held run has no
 * mouse and cannot right-click or place blocks the way a player would, so the
 * same beats are restated here as a {@code need} (what the arc wants at this
 * beat) plus a {@code how} (a way the agent can get there without a
 * right-click: a {@code _qtdev} command where one does the action and its
 * side effects, a vanilla command where the action has no mod side effect, or
 * an honest "needs a player" note where nothing agent-side can do it). Those
 * "needs a player" notes are exactly the stumbling points the agent is meant
 * to find.
 *
 * <p>The {@code need} text mirrors the chicken's plain hint intent (the
 * imperative fourth-wall line in {@code ChickenArcPresentation}) but is
 * reworded for an agent that cannot right-click; keep the two aligned if the
 * chicken's hints change. This is a separate mapping, not a reuse of
 * {@code ChickenArcPresentation}, because the chicken's leaf keys are
 * right-click imperatives an agent can't act on.
 */
final class AgentBeatGuidance {

    private AgentBeatGuidance() {
    }

    /** The current beat's agent-facing guidance, or {@code null} for terminal beats. */
    record Guidance(String need, String how) {
    }

    @Nullable
    static Guidance forBeat(ChickenBeatState state) {
        return switch (state) {
            case WAITING_FOR_STICK -> new Guidance(
                    "Get a town wand in hand - this claims the flag.",
                    "No _qtdev command issues a wand. Obtain one (e.g. /give).");
            case WAITING_FOR_WAND_ON_CAMPFIRE -> new Guidance(
                    "Light the campfire with the town wand.",
                    "No _qtdev command lights the campfire - it is a wand right-click, "
                            + "so this needs a player action (a stumbling point for a headless agent).");
            case SUNSET_AND_MAP -> new Guidance(
                    "Return at night and sleep on the campfire (via the wand).",
                    "No _qtdev command performs the campfire sleep. Warp toward night with "
                            + "qt debug warp <flag pos> <ticks>, but the sleep itself needs a player "
                            + "- likely a stumbling point.");
            case WAITING_FOR_WALL_BLOCK -> new Guidance(
                    "Place a solid block in the wall gap.",
                    "/setblock <gap pos> cobblestone (a plain block has no mod side effect, so /setblock works).");
            case WAITING_FOR_DOOR -> new Guidance(
                    "Place a door in the wall gap.",
                    "/setblock <gap pos> oak_door.");
            case WAITING_FOR_WAND_ON_DOOR -> new Guidance(
                    "Register the room by marking the door.",
                    "/_qtdev registerroom <door pos>.");
            case WAITING_FOR_SIGN -> new Guidance(
                    "Place a sign in the room (it converts to a job board).",
                    "/_qtdev place_board <sign pos>.");
            case WAITING_FOR_CHEST -> new Guidance(
                    "Place a chest in the room.",
                    "/setblock <pos> chest (note: /setblock will not register it as a town "
                            + "container - deposit via /_qtdev deposit <item> <count>).");
            case WAITING_FOR_PRESSURE_PLATE -> new Guidance(
                    "Craft a welcome mat at the flag and place it at the gate.",
                    "/_qtdev place_mat <gate pos>.");
            case WAITING_FOR_FREE_FOOD -> new Guidance(
                    "Free-food bonus: right-click the flag with a block of dirt (optional beat).",
                    "Optional - you can skip it; it auto-completes when the next beat is met.");
            case WAITING_FOR_VILLAGER_UI -> new Guidance(
                    "Open a villager's menu (right-click a villager).",
                    "No _qtdev command opens a villager menu - needs a player action "
                            + "(a stumbling point for a headless agent).");
            case WAITING_FOR_FLAG_UI -> new Guidance(
                    "Open the flag's menu (right-click the flag).",
                    "No _qtdev command opens the flag menu - needs a player action "
                            + "(a stumbling point for a headless agent).");
            case AWAITING_WORLDLY_SEEDS_DELIVERY -> new Guidance(
                    "Deliver Worldly Seeds to the town (into a chest in the room).",
                    "/_qtdev deposit <item> <count>.");
            case COMPLETE, FORFEIT -> null;
        };
    }

    /** Short map label for a beat; terminal states fold into "done". */
    static String shortLabel(ChickenBeatState state) {
        return switch (state) {
            case WAITING_FOR_STICK -> "wand";
            case WAITING_FOR_WAND_ON_CAMPFIRE -> "campfire";
            case SUNSET_AND_MAP -> "sleep";
            case WAITING_FOR_WALL_BLOCK -> "wall";
            case WAITING_FOR_DOOR -> "door";
            case WAITING_FOR_WAND_ON_DOOR -> "register";
            case WAITING_FOR_SIGN -> "jobboard";
            case WAITING_FOR_CHEST -> "chest";
            case WAITING_FOR_PRESSURE_PLATE -> "mat";
            case WAITING_FOR_FREE_FOOD -> "food";
            case WAITING_FOR_VILLAGER_UI -> "villager";
            case WAITING_FOR_FLAG_UI -> "flag";
            case AWAITING_WORLDLY_SEEDS_DELIVERY -> "seeds";
            case COMPLETE -> "done";
            case FORFEIT -> "forfeited";
        };
    }

    /**
     * A compact map of the whole arc (every beat, in order) with the current one
     * marked, so an agent sees all the beats the chicken walks without a
     * step-by-step tutorial. Terminal states are not part of the walkable map.
     */
    static String mapLine(ChickenBeatState current) {
        StringBuilder sb = new StringBuilder("map:");
        for (ChickenBeatState beat : ChickenBeatState.values()) {
            if (beat == ChickenBeatState.FORFEIT) {
                continue;
            }
            String label = shortLabel(beat);
            sb.append(' ')
                    .append(beat == current ? "*" + label + "*" : label);
            if (beat != ChickenBeatState.COMPLETE) {
                sb.append(" >");
            }
        }
        return sb.toString();
    }

    /**
     * 1-based position of {@code state} among the walkable beats (excludes the
     * terminal COMPLETE and FORFEIT), or 0 for a terminal state.
     */
    static int walkableIndex(ChickenBeatState state) {
        if (state == ChickenBeatState.COMPLETE || state == ChickenBeatState.FORFEIT) {
            return 0;
        }
        int i = 0;
        for (ChickenBeatState beat : ChickenBeatState.values()) {
            if (beat == ChickenBeatState.COMPLETE || beat == ChickenBeatState.FORFEIT) {
                continue;
            }
            i++;
            if (beat == state) {
                return i;
            }
        }
        return 0;
    }

    static int walkableCount() {
        int count = 0;
        for (ChickenBeatState beat : ChickenBeatState.values()) {
            if (beat != ChickenBeatState.COMPLETE && beat != ChickenBeatState.FORFEIT) {
                count++;
            }
        }
        return count;
    }
}

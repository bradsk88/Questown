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
    record Guidance(String need, String how, String teaches) {
    }

    @Nullable
    static Guidance forBeat(ChickenBeatState state) {
        return switch (state) {
            case WAITING_FOR_STICK -> new Guidance(
                    "Right-click the flag with a stick to get the town wand (this claims it).",
                    "The flag turns a stick into a town wand when a player right-clicks it with a stick, "
                            + "and the arc advances once that player is within ~16 blocks holding the wand. "
                            + "A headless agent has no mouse, so it uses: /_qtdev clickwand <flag pos> "
                            + "(it gives the player a stick and does the real flag click, which converts it to "
                            + "the wand). The player must be in a player mode near the flag - do not use "
                            + "qt tp, it puts the dev in spectator and a spectator does not satisfy the beat.",
                    "The chicken reacts to a player standing near the flag, not to a wand in any "
                            + "inventory. A player far away sees a chicken that does nothing and can not "
                            + "tell why - presence, not the item, is the trigger.");
            case WAITING_FOR_WAND_ON_CAMPFIRE -> new Guidance(
                    "Light a campfire near the flag.",
                    "The beat needs a lit campfire within the flag's campfire-search radius; it does not "
                            + "need a wand or a player nearby. /setblock <campfire pos> campfire[lit=true] "
                            + "advances it; a plain /setblock campfire is unlit and will not.",
                    "The beat keys off a lit campfire in range, so the name reads as a wand action it is "
                            + "not. A player who built the campfire the normal way does not know it also "
                            + "has to be lit, but a lit setblock settles it with no wand.");
            case SUNSET_AND_MAP -> new Guidance(
                    "Return at night and sleep on the campfire (via the wand).",
                    "The beat advances on a campfire sleep (a flag bit). No _qtdev command performs the "
                            + "sleep - needs a player. Likely a stumbling point.",
                    "The arc gates on a real night-time campfire sleep with no fast-path. A player who "
                            + "finishes the build before dusk sees the town stop responding and may read it "
                            + "as broken rather than waiting.");
            case WAITING_FOR_WALL_BLOCK -> new Guidance(
                    "Place a solid block in the wall gap.",
                    "/setblock <gap pos> cobblestone (a plain block has no mod side effect, so /setblock works).",
                    "The chicken wants a block in one specific gap, signalled only by where it pecks. A "
                            + "player who can not find the gap can not tell what is wanted.");
            case WAITING_FOR_DOOR -> new Guidance(
                    "Place a door in the wall gap.",
                    "/setblock <gap pos> oak_door.",
                    "As with the wall, the door goes in one gap signalled only by the chicken's peck. "
                            + "Progress is invisible until the chicken notices - legibility depends on "
                            + "watching it.");
            case WAITING_FOR_WAND_ON_DOOR -> new Guidance(
                    "Register the room by marking the door.",
                    "/_qtdev registerroom <door pos>.",
                    "Registering a room is a hidden wand right-click with no visible result beyond the "
                            + "room being detected. A player may not know the action 'took.'");
            case WAITING_FOR_SIGN -> new Guidance(
                    "Place a sign in the room (it converts to a job board).",
                    "/_qtdev place_board <sign pos> (the block must be inside the registered room so the "
                            + "room recipe matches).",
                    "A placed sign silently becomes a job board when the recipe matches - the change is "
                            + "invisible until then, so a player may not notice the sign 'did something.'");
            case WAITING_FOR_CHEST -> new Guidance(
                    "Place a chest in the room.",
                    "/setblock <pos> chest inside the registered room (the beat needs it in a "
                            + "recipe-matched room; /setblock alone will not make it a town container).",
                    "The chest only counts inside the registered room; a chest elsewhere is ignored, so a "
                            + "player placing a chest and seeing no progress has no obvious reason why.");
            case WAITING_FOR_PRESSURE_PLATE -> new Guidance(
                    "Craft a welcome mat at the flag and place it at the gate.",
                    "/_qtdev place_mat <gate pos>.",
                    "The welcome mat is crafted at the flag, not taken from an inventory - a non-obvious "
                            + "step. A player looking for the mat in their pack will not find it.");
            case WAITING_FOR_FREE_FOOD -> new Guidance(
                    "Free-food bonus: right-click the flag with a block of dirt (optional beat).",
                    "Optional - you can skip it; it auto-completes when the next beat is met.",
                    "This is the only skippable beat - it teaches that not every beat is required, but the "
                            + "arc gives no signal that this one is optional, so a player may stall here.");
            case WAITING_FOR_VILLAGER_UI -> new Guidance(
                    "Open a villager's menu (right-click a villager).",
                    "No _qtdev command opens a villager menu - needs a player action "
                            + "(a stumbling point for a headless agent).",
                    "Opening a villager's menu is the onboarding step, but players interact with villagers "
                            + "for other reasons too - the action is not marked as special.");
            case WAITING_FOR_FLAG_UI -> new Guidance(
                    "Open the flag's menu (right-click the flag).",
                    "No _qtdev command opens the flag menu - needs a player action "
                            + "(a stumbling point for a headless agent).",
                    "Opening the flag's menu is the step, but a player may have used the flag before and "
                            + "not realise opening its menu is the onboarding action.");
            case AWAITING_WORLDLY_SEEDS_DELIVERY -> new Guidance(
                    "Hand Worldly Seeds to the chicken (right-click it with the seeds).",
                    "The beat advances on a player right-clicking the chicken with the seeds (a flag bit). "
                            + "/_qtdev deposit only fills a container - it drives the presentation phase, not "
                            + "the advance, so it does not complete this beat. Needs a player (a stumbling point).",
                    "The seeds must be handed to the chicken, not merely left in a chest - a subtle "
                            + "distinction a player may miss when the chest already 'has' the seeds.");
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

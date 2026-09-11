package ca.bradj.questown.commands;

import ca.bradj.questown.mobs.helperchicken.ChickenBeatState;
import org.jetbrains.annotations.Nullable;

/**
 * Agent-facing guidance for the helper-chicken onboarding arc, surfaced by
 * {@code /_qtdev help}.
 *
 * <p>The helper chicken shows a player a bubble icon and a right-click hint
 * (see {@code ChickenArcPresentation}). An agent driving a held run has no
 * mouse, so the same beats are restated here as a {@code need} (the action the
 * town wants, in the player's terms) and a {@code how} (the world interaction
 * that fulfils it, also in the player's terms - what a player would do). The
 * text names the interaction, not the command that performs it: a headless
 * agent maps the interaction onto the tools it has (a {@code _qtdev} command, a
 * vanilla command, or a player action) itself. Where no agent-side action can do
 * the step, the {@code how} says so plainly, and that is the stumbling point the
 * agent is meant to find.
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
                    "Claim the flag with the town wand.",
                    "Right-click the flag with a stick; it becomes the town wand.",
                    "The chicken reacts to a player standing near the flag, not to a wand in any "
                            + "inventory. A player far away sees a chicken that does nothing and can not "
                            + "tell why - presence, not the item, is the trigger.");
            case WAITING_FOR_WAND_ON_CAMPFIRE -> new Guidance(
                    "Light a campfire near the flag.",
                    "Light a campfire near the flag (a lit one, not an unlit one).",
                    "The beat keys off a lit campfire in range, so the name reads as a wand action it is "
                            + "not. A player who built the campfire the normal way does not know it also "
                            + "has to be lit.");
            case SUNSET_AND_MAP -> new Guidance(
                    "Sleep on the campfire when it gets dark.",
                    "Use the town wand on the lit campfire to sleep: `_qtdev clickheld "
                            + "<campfire pos>`, from a player within a few blocks of it (a "
                            + "player too far gets `TOO_FAR_AWAY` and nothing happens); you "
                            + "wake at dawn. A campfire sleep will not start in daylight, so if "
                            + "it is still day set the clock to night first with `time set "
                            + "13000`. The night then passes on its own; if it ever holds, "
                            + "`time set <dawn>` forces the wake.",
                    "A player who finishes the build before dusk sees the town go quiet and may "
                            + "read it as broken rather than as waiting for dark. The agent sets "
                            + "the clock to night, then `clickheld` the campfire, and does not "
                            + "wait out a real night.");
            case WAITING_FOR_WALL_BLOCK -> new Guidance(
                    "Fill the wall gap the chicken is pointing at.",
                    "Place a solid block in the gap the chicken is pecking at.",
                    "The chicken wants a block in one specific gap, signalled only by where it pecks. A "
                            + "player who can not find the gap can not tell what is wanted.");
            case WAITING_FOR_DOOR -> new Guidance(
                    "Put a door in the wall gap the chicken is pointing at.",
                    "Place a full door (both halves) in the gap the chicken is pecking "
                            + "at with `_qtdev place_door <gap pos>`. Do not use `setblock`: it "
                            + "lays only the lower half of a door, leaving a one-block gap that "
                            + "then gets roofed over and breaks the door.",
                    "As with the wall, the door goes in one gap signalled only by the chicken's "
                            + "peck. Progress is invisible until the chicken notices - legibility "
                            + "depends on watching it. A door is two blocks; `setblock` puts only "
                            + "one, so it leaves a gap rather than a door and the beat never "
                            + "advances. `place_door` lays both halves the way a player using a "
                            + "door item would.");
            case WAITING_FOR_WAND_ON_DOOR -> new Guidance(
                    "Mark the door to register the room.",
                    "Right-click the door with the town wand to register the room it stands in.",
                    "Registering a room is a hidden wand right-click with no visible result beyond the "
                            + "room being detected. A player may not know the action 'took.'");
            case WAITING_FOR_SIGN -> new Guidance(
                    "Put a sign in the registered room.",
                    "Put a sign inside the registered room - it becomes a job board. "
                            + "This is a block you place, not an action you perform with a held "
                            + "item; the chicken's current hint may describe holding and using a "
                            + "sign, but placing it is a block placement, not a click. If no room is "
                            + "registered yet, register the door first (right-click the door with "
                            + "the town wand) so the room exists before the sign counts.",
                    "A placed sign silently becomes a job board when the recipe matches - the change is "
                            + "invisible until then, so a player may not notice the sign 'did something.' "
                            + "A player may also place a sign before any room is registered and see no "
                            + "progress, with no obvious reason why.");
            case WAITING_FOR_CHEST -> new Guidance(
                    "Put a chest in the registered room.",
                    "Place a chest inside the registered room.",
                    "The chest only counts inside the registered room; a chest elsewhere is ignored, so a "
                            + "player placing a chest and seeing no progress has no obvious reason why.");
            case WAITING_FOR_ROOM_BUILD -> new Guidance(
                    "The chicken is building the room around the door.",
                    "No action is needed; the chicken is building the room itself, one block at a "
                            + "time, until the registered door detects an enclosed room. Watch it finish "
                            + "and the next beat begins.",
                    "This beat is the only one in the arc the player does not drive; a player may not "
                            + "know why the chicken is laying blocks, and may think they need to do "
                            + "something while nothing is in fact required.");
            case WAITING_FOR_PRESSURE_PLATE -> new Guidance(
                    "Make a welcome mat and put it on the gate.",
                    "Craft a welcome mat at the flag, then place it at the gate the chicken points to.",
                    "The welcome mat is crafted at the flag, not taken from an inventory - a non-obvious "
                            + "step. A player looking for the mat in their pack will not find it.");
            case WAITING_FOR_FREE_FOOD -> new Guidance(
                    "Feed the flag free food.",
                    "Run `_qtdev free_food` to put food in the registered chest; it feeds the flag and "
                            + "advances the beat.",
                    "Feed the flag. Without it a villager that runs out of food can leave town, and the "
                            + "town gives no signal that the food is needed, so a player may sit idle "
                            + "expecting the beat to resolve on its own.");
            case WAITING_FOR_VILLAGER_UI -> new Guidance(
                    "Look at a villager.",
                    "Right-click a villager to open its menu, or use `_qtdev inspect <flag> villagers` "
                            + "to look at the villager and advance this beat.",
                    "Opening a villager's menu is the onboarding step, but players interact with villagers "
                            + "for other reasons too - the action is not marked as special.");
            case WAITING_FOR_FLAG_UI -> new Guidance(
                    "Look at the flag.",
                    "Right-click the flag to open its menu, or use `_qtdev inspect <flag>` "
                            + "to look at the flag menu and advance this beat.",
                    "Opening the flag's menu is the step, but a player may have used the flag before and "
                            + "not realise opening its menu is the onboarding action.");
            case AWAITING_WORLDLY_SEEDS_DELIVERY -> new Guidance(
                    "Hand the Worldly Seeds to the chicken.",
                    "Right-click the chicken while holding the Worldly Seeds. Leaving them in a chest does "
                            + "not count. No agent-side action does this; it needs a player. Likely a stumbling point.",
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
            case WAITING_FOR_ROOM_BUILD -> "build";
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

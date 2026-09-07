package ca.bradj.questown.commands;

import ca.bradj.questown.QT;
import ca.bradj.questown.mobs.helperchicken.ChickenArcConditions;
import ca.bradj.questown.mobs.helperchicken.ChickenArcTransitions;
import ca.bradj.questown.mobs.helperchicken.ChickenBeatState;
import ca.bradj.questown.town.entity.TownFlagBlockEntity;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import org.jetbrains.annotations.Nullable;

/**
 * Dev-only: give an agent driving a held run the same onboarding awareness the
 * helper chicken gives a human player, but as text it can read from the server
 * log and act on without a mouse.
 *
 * <p>The helper chicken points a player at the current beat with a bubble icon
 * and a right-click hint; an agent can't see the world and can't right-click or
 * place blocks, so this command restates the current beat as a {@code need}, a
 * {@code how}, and a {@code teaches} (the legibility gap the beat exposes - see
 * {@link AgentBeatGuidance}) plus a compact map of the whole arc, so the agent
 * knows where it is and how to get the next beat done without holding its hands
 * too much.
 *
 * <p>The agent is an observer, not the one holding a wand, so it reads the
 * flag's persisted {@link ChickenBeatState} (the same state the chicken's
 * controller advances) rather than any live chicken entity. A {@code status}
 * line reports whether the arc is even running ({@code chickenEverSpawned}/
 * forfeited) and whether the current beat's condition is met, so a stale beat
 * does not read as progress. No permission gate, so it runs from the console
 * (FIFO). Registered like the other {@code _qtdev} commands, targeting the first
 * flag with a position.
 */
public class QtDevHelpCommand {
    public static void register(CommandDispatcher<CommandSourceStack> src) {
        // @formatter:off
        src.register(
            Commands.literal("_qtdev").then(
                Commands.literal("help")
                    .executes(css -> run(css.getSource()))
            )
        );
        // @formatter:on
    }

    private static int run(CommandSourceStack source) {
        TownFlagBlockEntity flag = firstFlag();
        if (flag == null) {
            QT.FLAG_LOGGER.warn("[_qtdev help] no town flag found");
            return 1;
        }
        ChickenBeatState state = flag.getChickenBeatState();
        QT.FLAG_LOGGER.info("[_qtdev help] flag at {} - beat {}/{} ({})",
                flag.getBlockPos(), AgentBeatGuidance.walkableIndex(state),
                AgentBeatGuidance.walkableCount(), state);

        if (state == ChickenBeatState.COMPLETE) {
            QT.FLAG_LOGGER.info("[_qtdev help] onboarding complete - the arc is done.");
            QT.FLAG_LOGGER.info(AgentBeatGuidance.mapLine(state));
            return 0;
        }
        if (state == ChickenBeatState.FORFEIT) {
            QT.FLAG_LOGGER.info("[_qtdev help] arc forfeited - the chicken will not guide further.");
            return 0;
        }

        AgentBeatGuidance.Guidance guidance = AgentBeatGuidance.forBeat(state);
        if (guidance == null) {
            QT.FLAG_LOGGER.warn("[_qtdev help] no guidance for beat {}", state);
            return 1;
        }
        QT.FLAG_LOGGER.info(AgentBeatGuidance.mapLine(state));
        QT.FLAG_LOGGER.info("[_qtdev help] need:    {}", guidance.need());
        QT.FLAG_LOGGER.info("[_qtdev help] how:     {}", guidance.how());
        QT.FLAG_LOGGER.info("[_qtdev help] teaches: {}", guidance.teaches());
        QT.FLAG_LOGGER.info("[_qtdev help] status:  {}", statusLine(flag, state));
        return 0;
    }

    /**
     * Why the current beat is stuck, read from the flag's live state so a stale
     * beat is not mistaken for progress. The chicken's controller only advances
     * the beat when the arc is running ({@code chickenEverSpawned} and not
     * forfeited); otherwise the persisted beat is frozen no matter what the
     * player does. When the arc is running, reports whether the beat's condition
     * is met via the same {@link ChickenArcTransitions#isSatisfied} the
     * controller uses, so the agent knows the beat is inert, not just incomplete.
     */
    private static String statusLine(TownFlagBlockEntity flag, ChickenBeatState state) {
        if (!flag.getChickenEverSpawned()) {
            return "not running - the helper chicken has never spawned (this flag was not placed in an "
                    + "empty_town structure). The beat is stale and will not advance until a chicken spawns.";
        }
        if (flag.getChickenArcForfeit()) {
            return "the arc is forfeited; it will not advance.";
        }
        ChickenArcTransitions.Observed observed = ChickenArcConditions.observe(flag);
        if (ChickenArcTransitions.isSatisfied(state, observed)) {
            return "running; this beat's condition is met - it should advance on the next tick.";
        }
        return "running; this beat's condition is not met yet (the action in need/how has not happened).";
    }

    private static @Nullable TownFlagBlockEntity firstFlag() {
        for (TownFlagBlockEntity f : ca.bradj.questown.town.entity.TownFlags.all()) {
            if (f.getBlockPos() != null) {
                return f;
            }
        }
        return null;
    }
}

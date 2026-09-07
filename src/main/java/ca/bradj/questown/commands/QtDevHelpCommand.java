package ca.bradj.questown.commands;

import ca.bradj.questown.QT;
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
 * place blocks, so this command restates the current beat as a {@code need}
 * plus a {@code how} (see {@link AgentBeatGuidance}) and a compact map of the
 * whole arc, so the agent knows where it is and how to get the next beat done
 * without holding its hands too much.
 *
 * <p>The agent is an observer, not the one holding a wand, so it reads the
 * flag's persisted {@link ChickenBeatState} (the same state the chicken's
 * controller advances) rather than any live chicken entity. No permission gate,
 * so it runs from the console (FIFO). Registered like the other {@code _qtdev}
 * commands, targeting the first flag with a position.
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
        QT.FLAG_LOGGER.info("[_qtdev help] need: {}", guidance.need());
        QT.FLAG_LOGGER.info("[_qtdev help] how:  {}", guidance.how());
        return 0;
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

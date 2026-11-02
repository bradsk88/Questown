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

/** Dev-only: the helper chicken's current lesson as text, with the whole arc and whether the lesson is met. */
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

    /** Why the current lesson is not moving: the arc is not running, or its condition is not met. */
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

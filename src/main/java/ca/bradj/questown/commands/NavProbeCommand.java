package ca.bradj.questown.commands;

import ca.bradj.questown.QT;
import ca.bradj.questown.mobs.visitor.VisitorMobEntity;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

import java.util.UUID;

// Dev-only, read-only: a villager's walk target, path end, and whether it can reach it.
public class NavProbeCommand {

    public static void register(CommandDispatcher<CommandSourceStack> src) {
        // @formatter:off
        src.register(
            Commands.literal("_qtdev").then(
                Commands.literal("nav")
                    .then(Commands.argument("villager", UuidArgument.uuid())
                        .executes(css -> run(
                                css.getSource().getLevel(),
                                UuidArgument.getUuid(css, "villager"))))
            )
        );
        // @formatter:on
    }

    private static int run(ServerLevel level, UUID villagerUUID) {
        if (level == null) {
            QT.VILLAGER_LOGGER.warn("[_qtdev nav] no level yet (server still starting)");
            return 1;
        }
        Entity e = level.getEntity(villagerUUID);
        if (!(e instanceof VisitorMobEntity vme)) {
            QT.VILLAGER_LOGGER.warn("[_qtdev nav] no villager {} in {}", villagerUUID, level.dimension());
            return 1;
        }
        QT.VILLAGER_LOGGER.info("[_qtdev nav] {} {}", villagerUUID, vme.describeNav());
        return 0;
    }
}

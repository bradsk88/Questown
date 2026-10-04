package ca.bradj.questown.commands;

import ca.bradj.questown.QT;
import ca.bradj.questown.integration.minecraft.MCTownItem;
import ca.bradj.questown.loader.Loader;
import ca.bradj.questown.town.entity.TownFlagBlockEntity;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.commands.arguments.coordinates.Coordinates;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

// Dev-only: fire the LOOT-quest-completion path (QuestsHolder.processLootQuests) with a
// chosen item in the live world, so the match can be verified without waiting for a
// leaver's extraction to fire on its own. No permission gate, so it runs from the
// console (FIFO) during a held run.
// DEV-DIAG: raw_food hunt investigation, 2026-10-02 — grep 'DEV-DIAG' to enumerate; keep/remove per handoff
public class DevLootFireCommand {

    public static void register(CommandDispatcher<CommandSourceStack> src) {
        RequiredArgumentBuilder<CommandSourceStack, Coordinates> posArg = Commands.argument(
                "pos", BlockPosArgument.blockPos()
        );

        // @formatter:off
        src.register(
            Commands.literal("_qtdev").then(
                Commands.literal("loot_fire")
                    .then(posArg
                    .then(Commands.argument("item", StringArgumentType.word())
                        .executes(css -> run(
                            css.getSource(),
                            BlockPosArgument.getLoadedBlockPos(css, "pos"),
                            css.getArgument("item", String.class)
                        ))))
            )
        );
        // @formatter:on
    }

    private static int run(CommandSourceStack source, BlockPos target, String itemName) {
        TownFlagBlockEntity tfbe = QTCommands.getFlagOrBroadcast(source, target);
        if (tfbe == null) {
            return -1;
        }
        Item item = Loader.itemFromId(new ResourceLocation(itemName));
        if (item == null) {
            QT.FLAG_LOGGER.info("[_qtdev loot_fire] unknown item {}", itemName);
            return -1;
        }
        QT.FLAG_LOGGER.info("[_qtdev loot_fire] firing processLootQuests with {} x1", itemName);
        tfbe.getQuestHandle().processLootQuests(MCTownItem.fromMCItemStack(new ItemStack(item, 1)));
        return 0;
    }
}

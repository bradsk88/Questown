package ca.bradj.questown.commands;

import ca.bradj.questown.QT;
import ca.bradj.questown.integration.minecraft.MCContainer;
import ca.bradj.questown.integration.minecraft.MCTownItem;
import ca.bradj.questown.jobs.leaver.ContainerTarget;
import ca.bradj.questown.town.TownContainers;
import ca.bradj.questown.town.entity.TownFlagBlockEntity;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

// Dev-only: the helper-chicken "free food" beat is performed by a player
// right-clicking the flag with a block of dirt, but the flag's giveBonusFood
// only drops a carrot ItemEntity at the flag — it feeds no one. A held run has
// no mouse to do that action anyway. This command is the meaningful version:
// it deposits food into a registered container (the same path a player depositing
// into a chest takes, see DepositItemCommand) and flips the once-only bonus-food
// bit so the beat advances. Enforces the same "only once" rule as giveBonusFood.
public class FreeFoodCommand {
    public static void register(CommandDispatcher<CommandSourceStack> src) {
        src.register(
            Commands.literal("_qtdev").then(
                Commands.literal("free_food")
                    .executes(css -> run(css.getSource()))
            )
        );
    }

    private static int run(CommandSourceStack source) {
        for (TownFlagBlockEntity flag : ca.bradj.questown.town.entity.TownFlags.all()) {
            if (flag.getBlockPos() == null) {
                continue;
            }
            if (flag.getGivenBonusFood()) {
                QT.FLAG_LOGGER.warn("[free_food] bonus food already given for flag {}", flag.getUUID());
                return 1;
            }
            boolean any = false;
            for (ContainerTarget<MCContainer, MCTownItem> target :
                    TownContainers.getAllContainers(flag, source.getLevel())) {
                if (source.getLevel().getBlockEntity(target.getBlockPos()) instanceof Container c) {
                    ItemStack stack = new ItemStack(Items.CARROT, 10);
                    for (int i = 0; i < c.getContainerSize() && !stack.isEmpty(); i++) {
                        if (c.getItem(i).isEmpty()) {
                            c.setItem(i, stack.copy());
                            stack.setCount(0);
                        }
                    }
                    c.setChanged();
                    any = true;
                }
            }
            if (!any) {
                QT.FLAG_LOGGER.warn("[free_food] no registered container for flag {}", flag.getUUID());
                return 1;
            }
            // Only flip the once-only bit after the food is actually in a container,
            // so a run with no registered chest does not advance the beat empty-handed.
            flag.markBonusFoodGiven();
            QT.FLAG_LOGGER.info("[free_food] deposited 10 carrots into a registered container for flag {}",
                    flag.getUUID());
            return 0;
        }
        QT.FLAG_LOGGER.warn("[free_food] no flag to give bonus food for");
        return 1;
    }
}

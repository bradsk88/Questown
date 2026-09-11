package ca.bradj.questown.commands;

import ca.bradj.questown.QT;
import ca.bradj.questown.integration.minecraft.MCContainer;
import ca.bradj.questown.integration.minecraft.MCTownItem;
import ca.bradj.questown.jobs.leaver.ContainerTarget;
import ca.bradj.questown.town.TownContainers;
import ca.bradj.questown.town.entity.TownFlagBlockEntity;
import ca.bradj.questown.town.quests.MCQuest;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.AbstractMap;

// Dev-only: an open item quest (here only the wooden sword) is cleared by the
// real player path of leaving the item in the storeroom the game shows you,
// not by a magic clear. Most item supply is the villagers' own jobs; the
// wooden sword is the one item quest a player supplies. This command does
// exactly that: it deposits the wanted item into a registered container, the
// same path a player dropping the item in the chest takes, so the quest's
// supply-chain lesson is preserved. Scoped to the wooden sword only - other
// item needs are the townies' job, not a command.
public class SupplySwordCommand {
    public static void register(CommandDispatcher<CommandSourceStack> src) {
        src.register(
            Commands.literal("_qtdev").then(
                Commands.literal("supply_sword")
                    .executes(css -> run(css.getSource()))
            )
        );
    }

    private static int run(CommandSourceStack source) {
        for (TownFlagBlockEntity flag : ca.bradj.questown.town.entity.TownFlags.all()) {
            if (flag.getBlockPos() == null) {
                continue;
            }
            MCQuest swordQuest = openSwordQuest(flag);
            if (swordQuest == null) {
                QT.FLAG_LOGGER.warn("[supply_sword] no open wooden_sword quest for flag {}", flag.getUUID());
                return 1;
            }
            Item item = ForgeRegistries.ITEMS.getValue(swordQuest.getWantedId());
            if (item == null || item == Items.AIR) {
                QT.FLAG_LOGGER.warn("[supply_sword] cannot resolve the wanted item for flag {}", flag.getUUID());
                return 1;
            }
            if (deposit(flag, source, item, swordQuest.getCountNeeded())) {
                QT.FLAG_LOGGER.info("[supply_sword] deposited {}x {} into a registered container (real player path) for flag {}",
                        swordQuest.getCountNeeded(), swordQuest.getWantedId(), flag.getUUID());
                return 0;
            }
            QT.FLAG_LOGGER.warn("[supply_sword] no registered container for flag {}; supply the sword in a town chest to clear it",
                    flag.getUUID());
            return 1;
        }
        QT.FLAG_LOGGER.warn("[supply_sword] no flag to supply the sword for");
        return 1;
    }

    /** The open (incomplete) wooden_sword quest on the flag, or null. */
    private static MCQuest openSwordQuest(TownFlagBlockEntity flag) {
        ResourceLocation wanted = ca.bradj.questown.mc.Compat.getItemId(Items.WOODEN_SWORD);
        for (AbstractMap.SimpleEntry<MCQuest, ?> e : flag.getQuestHandle().getAllQuestsWithRewards()) {
            MCQuest q = e.getKey();
            if (q.isComplete() || q.getWantedId() == null) {
                continue;
            }
            if (wanted.equals(q.getWantedId())) {
                return q;
            }
        }
        return null;
    }

    /** Deposit the wanted item into the first registered town container,
     * as a player dropping the item in the chest. Returns false if the town
     * has no container, so a blocked step reads as a real dead end. */
    private static boolean deposit(TownFlagBlockEntity flag, CommandSourceStack source, Item item, int count) {
        for (ContainerTarget<MCContainer, MCTownItem> target :
                TownContainers.getAllContainers(flag, source.getLevel())) {
            if (source.getLevel().getBlockEntity(target.getBlockPos()) instanceof Container c) {
                ItemStack stack = new ItemStack(item, count);
                for (int i = 0; i < c.getContainerSize() && !stack.isEmpty(); i++) {
                    if (c.getItem(i).isEmpty()) {
                        c.setItem(i, stack.copy());
                        stack.setCount(0);
                    }
                }
                c.setChanged();
                return stack.isEmpty();
            }
        }
        return false;
    }
}

package ca.bradj.questown.commands;
import ca.bradj.questown.loader.Loader;

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
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;

// Dev-only: put the sword an open sword quest wants into a town chest. Once per quest.
public class SupplySwordCommand {
    public static void register(CommandDispatcher<CommandSourceStack> src) {
        for (String literal : new String[]{"complete_sword_quest", "supply_sword"}) {
            src.register(
                Commands.literal("_qtdev").then(
                    Commands.literal(literal)
                        .executes(css -> run(css.getSource()))
                )
            );
        }
    }

    /** Completes the flag's open sword quests by putting the swords in its chests. True if any was completed. */
    public static boolean completeSwordQuestsForFlag(TownFlagBlockEntity flag, net.minecraft.server.level.ServerLevel level) {
        List<MCQuest> swordQuests = openSwordQuests(flag);
        if (swordQuests.isEmpty()) {
            return false;
        }
        net.minecraft.commands.CommandSourceStack source = level.getServer().createCommandSourceStack().withPermission(4);
        boolean any = false;
        for (MCQuest quest : swordQuests) {
            Item item = Loader.itemFromId(quest.getWantedId());
            if (item == null || item == Items.AIR) {
                continue;
            }
            DepositResult result = deposit(flag, source, item, quest.getCountNeeded());
            if (result == DepositResult.DEPOSITED) {
                QT.FLAG_LOGGER.info("[complete_sword_quest] deposited {}x {} into a registered container (real player path) for flag {}",
                        quest.getCountNeeded(), quest.getWantedId(), flag.getUUID());
                any = true;
            }
        }
        return any;
    }

    private static int run(CommandSourceStack source) {
        for (TownFlagBlockEntity flag : ca.bradj.questown.town.entity.TownFlags.all()) {
            if (flag.getBlockPos() == null) {
                continue;
            }
            List<MCQuest> swordQuests = openSwordQuests(flag);
            if (swordQuests.isEmpty()) {
                QT.FLAG_LOGGER.warn("[complete_sword_quest] no open sword quest for flag {}", flag.getUUID());
                return 1;
            }
            boolean all = true;
            for (MCQuest quest : swordQuests) {
                Item item = Loader.itemFromId(quest.getWantedId());
                if (item == null || item == Items.AIR) {
                    QT.FLAG_LOGGER.warn("[complete_sword_quest] cannot resolve the wanted item {} for flag {}",
                            quest.getWantedId(), flag.getUUID());
                    all = false;
                    continue;
                }
                DepositResult result = deposit(flag, source, item, quest.getCountNeeded());
                if (result == DepositResult.DEPOSITED) {
                    QT.FLAG_LOGGER.info("[complete_sword_quest] deposited {}x {} into a registered container (real player path) for flag {}",
                            quest.getCountNeeded(), quest.getWantedId(), flag.getUUID());
                } else if (result == DepositResult.ALL_FULL) {
                    QT.FLAG_LOGGER.warn("[complete_sword_quest] every registered container is full for flag {}; make space in a town chest to clear it",
                            flag.getUUID());
                    all = false;
                } else {
                    QT.FLAG_LOGGER.warn("[complete_sword_quest] no registered container for flag {}; supply the sword in a town chest to clear it",
                            flag.getUUID());
                    all = false;
                }
            }
            return all ? 0 : 1;
        }
        QT.FLAG_LOGGER.warn("[complete_sword_quest] no flag to supply the sword for");
        return 1;
    }

    /** The flag's open sword item quests. */
    private static List<MCQuest> openSwordQuests(TownFlagBlockEntity flag) {
        List<MCQuest> out = new ArrayList<>();
        for (AbstractMap.SimpleEntry<MCQuest, ?> e : flag.getQuestHandle().getAllQuestsWithRewards()) {
            MCQuest q = e.getKey();
            if (q.isComplete() || q.getWantedId() == null) {
                continue;
            }
            if (q.getType() != ca.bradj.questown.town.quests.Quest.QuestType.ITEM) {
                continue;
            }
            if (q.getWantedId().getPath().endsWith("_sword")) {
                out.add(q);
            }
        }
        return out;
    }

    private enum DepositResult { DEPOSITED, NO_CONTAINER, ALL_FULL }

    /** Puts the item in the first town chest with an empty slot. */
    private static DepositResult deposit(TownFlagBlockEntity flag, CommandSourceStack source, Item item, int count) {
        ItemStack stack = new ItemStack(item, count);
        boolean sawContainer = false;
        for (ContainerTarget<MCContainer, MCTownItem> target :
                TownContainers.getAllContainers(flag, source.getLevel())) {
            if (source.getLevel().getBlockEntity(target.getBlockPos()) instanceof Container c) {
                sawContainer = true;
                // One item per slot: swords do not stack.
                for (int i = 0; i < c.getContainerSize() && !stack.isEmpty(); i++) {
                    if (c.getItem(i).isEmpty()) {
                        int place = Math.min(stack.getCount(), stack.getMaxStackSize());
                        c.setItem(i, new ItemStack(item, place));
                        stack.shrink(place);
                    }
                }
                c.setChanged();
                if (stack.isEmpty()) {
                    return DepositResult.DEPOSITED;
                }
            }
        }
        return sawContainer ? DepositResult.ALL_FULL : DepositResult.NO_CONTAINER;
    }
}

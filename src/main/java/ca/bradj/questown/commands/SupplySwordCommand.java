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
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;

// Dev-only: an open sword quest is cleared by the real player path of leaving the
// item in the storeroom the game shows you, not by a magic clear. Most item supply
// is the villagers' own jobs; a sword quest (the wooden sword in onboarding, the
// stone sword after) is the one kind of item quest a player supplies. This command
// does exactly that: it deposits the wanted item into a registered container, the
// same path a player dropping the item in the chest takes, so the quest's
// supply-chain lesson is preserved. Scoped to sword quests only - other item needs
// are the townies' job, not a command. One time per quest, the way a quest
// completes: once the sword is in, the quest closes and a re-run finds no open
// sword quest (the same once-only shape as free_food's bonus-food bit).
public class SupplySwordCommand {
    public static void register(CommandDispatcher<CommandSourceStack> src) {
        // complete_sword_quest is the name an agent looks for; supply_sword is the
        // older literal, kept so existing runs and docs keep working.
        for (String literal : new String[]{"complete_sword_quest", "supply_sword"}) {
            src.register(
                Commands.literal("_qtdev").then(
                    Commands.literal(literal)
                        .executes(css -> run(css.getSource()))
                )
            );
        }
    }

    /** Complete every open sword quest for the given flag (and only that flag).
     *  Public entry point for scenario code (the self_suff seed) that needs the
     *  sword quest cleared through the real player path without driving the
     *  command. Returns true when at least one quest was deposited, false when
     *  there was no open sword quest on that flag. */
    public static boolean completeSwordQuestsForFlag(TownFlagBlockEntity flag, net.minecraft.server.level.ServerLevel level) {
        List<MCQuest> swordQuests = openSwordQuests(flag);
        if (swordQuests.isEmpty()) {
            return false;
        }
        net.minecraft.commands.CommandSourceStack source = level.getServer().createCommandSourceStack().withPermission(4);
        boolean any = false;
        for (MCQuest quest : swordQuests) {
            Item item = ForgeRegistries.ITEMS.getValue(quest.getWantedId());
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
                Item item = ForgeRegistries.ITEMS.getValue(quest.getWantedId());
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

    /** The open (incomplete) sword item quests on the flag (wooden_sword,
     * stone_sword, ...). Item type only: a job quest like hunter_qt_sword also
     * ends in "_sword" but names a job, not an item to deposit. */
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

    /** Deposit the wanted item into the first registered town container with an
     * empty slot, as a player dropping the item in the chest. Tries every
     * registered container: the first one may be full of gathered loot, and a
     * player would just open the next chest. NO_CONTAINER when the town has no
     * container at all (a real dead end), ALL_FULL when containers exist but
     * none has an empty slot. */
    private static DepositResult deposit(TownFlagBlockEntity flag, CommandSourceStack source, Item item, int count) {
        ItemStack stack = new ItemStack(item, count);
        boolean sawContainer = false;
        for (ContainerTarget<MCContainer, MCTownItem> target :
                TownContainers.getAllContainers(flag, source.getLevel())) {
            if (source.getLevel().getBlockEntity(target.getBlockPos()) instanceof Container c) {
                sawContainer = true;
                for (int i = 0; i < c.getContainerSize() && !stack.isEmpty(); i++) {
                    if (c.getItem(i).isEmpty()) {
                        c.setItem(i, stack.copy());
                        stack.setCount(0);
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

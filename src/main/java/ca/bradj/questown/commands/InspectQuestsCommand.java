package ca.bradj.questown.commands;

import ca.bradj.questown.QT;
import ca.bradj.questown.mc.Compat;
import ca.bradj.questown.town.entity.TownFlagBlockEntity;
import ca.bradj.questown.town.quests.MCQuest;
import ca.bradj.questown.town.quests.Quest;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.resources.ResourceLocation;
import ca.bradj.roomrecipes.serialization.MCRoom;

// Dev-only, read-only: a flag's active and complete quests, with what each wants.
public class InspectQuestsCommand {
    public static void register(CommandDispatcher<CommandSourceStack> src) {
        src.register(
            Commands.literal("qt").then(
                Commands.literal("quests")
                    .executes(css -> run(css.getSource()))
            )
        );
    }

    private static int run(CommandSourceStack source) {
        QT.FLAG_LOGGER.info("[questinspect] === quests ===");
        boolean any = false;
        for (TownFlagBlockEntity flag : ca.bradj.questown.town.entity.TownFlags.all()) {
            String flagId = flag.getBlockPos() != null
                    ? flag.getBlockPos().toShortString() : flag.getUUID().toString();
            QT.FLAG_LOGGER.info("[questinspect] flag {} (pos {}) red-dot-quests-tab={}",
                    flag.getUUID(), flagId, flag.getInfo().questsTabAttention());
            for (Quest<ResourceLocation, MCRoom> q : flag.getAllQuests()) {
                if (!(q instanceof MCQuest mcq)) {
                    continue;
                }
                any = true;
                QT.FLAG_LOGGER.info("[questinspect]   {} want={} type={} count={} owner={} onboarding={}",
                        mcq.getStatus(),
                        mcq.getWantedId(),
                        mcq.getType(),
                        mcq.getCountNeeded(),
                        mcq.getUUID(),
                        mcq.isOnboarding()
                );
                String detail = explain(mcq);
                if (detail != null) {
                    QT.FLAG_LOGGER.info("[questinspect]     wants: {}", detail);
                }
                if (mcq.fromRecipeID().isPresent()) {
                    QT.FLAG_LOGGER.info("[questinspect]     from: {}", mcq.fromRecipeID().get());
                }
            }
        }
        if (!any) {
            QT.FLAG_LOGGER.info("[questinspect] no flags / no quests");
        }
        QT.FLAG_LOGGER.info("[questinspect] === end ===");
        return 0;
    }

    /** What a quest wants, in player terms, or null. */
    private static String explain(MCQuest q) {
        Quest.QuestType type = q.getType();
        if (type == Quest.QuestType.ITEM) {
            String item = resolveItemName(q.getWantedId());
            return (item != null ? item : "item " + q.getWantedId()) + " x" + q.getCountNeeded();
        }
        if (type == Quest.QuestType.LOOT) {
            // Like ITEM, but a villager has to bring the item back, not the player.
            String item = resolveItemName(q.getWantedId());
            return (item != null ? item : "item " + q.getWantedId()) + " x" + q.getCountNeeded() + " (loot: completes on production)";
        }
        if (type == Quest.QuestType.ROOM) {
            return "room recipe " + q.getWantedId();
        }
        if (type == Quest.QuestType.CONCURRENT_JOBS) {
            return "jobs: " + q.getConcurrentJobIds();
        }
        if (type == Quest.QuestType.JOB_CHANGE) {
            return "job: " + q.getWantedId();
        }
        if (type == Quest.QuestType.SKILL_UNLOCK) {
            return "skill tree: " + q.getWantedId();
        }
        if (type == Quest.QuestType.VILLAGER_LEVEL) {
            return "villager level: " + q.getWantedId();
        }
        return null;
    }

    /** The name a player sees for an item or tag id. */
    private static String resolveItemName(ResourceLocation id) {
        if (id == null) {
            return null;
        }
        try {
            net.minecraft.world.item.crafting.Ingredient ing =
                    ca.bradj.questown.gui.Ingredients.fromString(id.getNamespace() + ":" + id.getPath());
            return ca.bradj.questown.gui.Ingredients.getName(ing).getString();
        } catch (Exception ignored) {
        }
        return null;
    }
}

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

// Dev-only: inspect the active (and complete) quests of a town flag, the way a
// player reading the flag's quest tab would. The held agent-run test observes
// progress through the [village] log, but the quest lines only print an opaque
// Quest{id=...} — the player question "what does this quest want?" was
// unanswerable. This command answers it: for each quest it prints the type, the
// count needed, and the concrete requirement (the block/item an ITEM quest wants;
// the room recipe name a ROOM quest wants). No permission gate, so it runs from
// the console (FIFO).
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
            QT.FLAG_LOGGER.info("[questinspect] flag {} (pos {})", flag.getUUID(), flagId);
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

    /** What a quest actually wants, in player terms. Null when not resolvable. */
    private static String explain(MCQuest q) {
        Quest.QuestType type = q.getType();
        if (type == Quest.QuestType.ITEM) {
            String item = resolveItemName(q.getWantedId());
            return (item != null ? item : "item " + q.getWantedId()) + " x" + q.getCountNeeded();
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
        return null;
    }

    /**
     * Resolve an item-want id to a name. The want may be a concrete item
     * (minecraft:bread) or a tag (questown:villager_food); either way
     * {@link ca.bradj.questown.gui.Ingredients#fromString} handles it. The
     * representative member's name is what a player sees in the need bubble.
     */
    private static String resolveItemName(ResourceLocation id) {
        if (id == null) {
            return null;
        }
        try {
            net.minecraft.world.item.crafting.Ingredient ing =
                    ca.bradj.questown.gui.Ingredients.fromString(id.getNamespace() + ":" + id.getPath());
            return ca.bradj.questown.gui.Ingredients.getName(ing).getString();
        } catch (Exception ignored) {
            // not an item or tag; fall through
        }
        return null;
    }
}

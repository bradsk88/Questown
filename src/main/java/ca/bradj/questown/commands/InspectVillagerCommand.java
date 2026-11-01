package ca.bradj.questown.commands;

import ca.bradj.questown.QT;
import ca.bradj.questown.gui.Ingredients;
import ca.bradj.questown.mobs.visitor.TownieNeed;
import ca.bradj.questown.mobs.visitor.VisitorMobEntity;
import ca.bradj.questown.town.entity.TownFlagBlockEntity;
import ca.bradj.questown.town.quests.MCQuestBatch;
import ca.bradj.questown.town.quests.Quest;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.crafting.Ingredient;

// Dev-only, read-only: each villager's need (the bubble, with the missing item for UNMET_ITEM), its
// quests, and the quests-tab attention cue.
public class InspectVillagerCommand {
    public static void register(CommandDispatcher<CommandSourceStack> src) {
        src.register(
            Commands.literal("_qtdev").then(
                Commands.literal("villagers")
                    .executes(css -> run(css.getSource()))
            )
        );
    }

    private static int run(CommandSourceStack source) {
        QT.FLAG_LOGGER.info("[villagerinspect] === villagers ===");
        boolean any = false;
        for (TownFlagBlockEntity flag : ca.bradj.questown.town.entity.TownFlags.all()) {
            String flagId = flag.getBlockPos() != null
                    ? flag.getBlockPos().toShortString() : flag.getUUID().toString();
            QT.FLAG_LOGGER.info("[villagerinspect] flag {} (pos {}) red-dot-quests-tab={}",
                    flag.getUUID(), flagId, flag.getInfo().questsTabAttention());
            for (LivingEntity e : flag.getVillagerHandle().entities()) {
                if (!(e instanceof VisitorMobEntity villager)) {
                    continue;
                }
                any = true;
                TownieNeed need = villager.getNeed();
                QT.FLAG_LOGGER.info("[villagerinspect]   villager {} job={} need={}",
                        villager.getUUID(), villager.getJobId(), need);
                if (need == TownieNeed.UNMET_ITEM) {
                    String name = resolveItemName(villager.getUnmetItem());
                    QT.FLAG_LOGGER.info("[villagerinspect]     needs item: {}{}",
                            name != null ? name : villager.getUnmetItem(),
                            name != null ? " (" + villager.getUnmetItem() + ")" : "");
                }
                for (MCQuestBatch batch : flag.getQuestHandle().getAllBatchesForVillager(villager.getUUID())) {
                    for (Quest<?, ?> q : batch.getAll()) {
                        QT.FLAG_LOGGER.info("[villagerinspect]     quest {} want={} type={} count={}",
                                q.getStatus(), q.getWantedId(), q.getType(), q.getCountNeeded());
                    }
                }
            }
        }
        if (!any) {
            QT.FLAG_LOGGER.info("[villagerinspect] no villagers");
        }
        QT.FLAG_LOGGER.info("[villagerinspect] === end ===");
        return 0;
    }

    /**
     * Resolve an unmet-item key (a concrete item or a tag such as questown:villager_food)
     * to the name a player reads in the need bubble. Null when not resolvable.
     */
    private static String resolveItemName(String itemKey) {
        if (itemKey == null || itemKey.isEmpty()) {
            return null;
        }
        try {
            Ingredient ing = Ingredients.fromString(itemKey);
            return Ingredients.getName(ing).getString();
        } catch (Exception ignored) {
            // not an item or tag; fall through
        }
        return null;
    }
}

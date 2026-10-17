package ca.bradj.questown.town.entity;

import ca.bradj.questown.QT;
import ca.bradj.questown.core.UtilClean;
import ca.bradj.questown.core.network.QuestownNetwork;
import ca.bradj.questown.core.network.ShowTutorialToastMessage;
import ca.bradj.questown.jobs.JobID;
import ca.bradj.questown.logic.RoomRecipes;
import ca.bradj.questown.mc.Compat;
import ca.bradj.questown.town.quests.MCQuest;
import ca.bradj.questown.town.quests.Quest;
import ca.bradj.roomrecipes.core.space.Position;
import ca.bradj.roomrecipes.serialization.MCRoom;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

public class TownMessages {
    // E2 (.scratch/day1-playtest-2026-10-11/quest-complete-chat-blast-pacing.md):
    // day-1 events fire in bursts (mat completion posts the basics batch, board
    // placement completes it, each broadcasts), and the player sees a wall of
    // chat within a second. Every broadcast goes through this flag-wide queue:
    // the first line sends immediately, the rest space out at this interval,
    // consecutive duplicates are dropped (masks - does not fix - the
    // double-firing in messages-print-twice.md).
    private static final int BROADCAST_SPACING_TICKS = 100;
    private final java.util.ArrayDeque<Component> pendingBroadcasts = new java.util.ArrayDeque<>();
    private long lastBroadcastAt = Long.MIN_VALUE;

    private ServerLevel level;

    public void initialize(ServerLevel t) {
        this.level = t;
    }

    void broadcastMessage(
            String key,
            Object... args
    ) {
        if (level == null) {
            QT.FLAG_LOGGER.debug("Skipping message broadcast before TownMessages was initialized: {} {}", key, args);
            return;
        }

        Component line = Compat.translatable(key, args);
        long now = level.getGameTime();
        if (lastBroadcastAt == Long.MIN_VALUE || now - lastBroadcastAt >= BROADCAST_SPACING_TICKS) {
            lastBroadcastAt = now;
            sendNow(line);
            QT.FLAG_LOGGER.info("Broadcasting message: {} {}", key, args);
        } else if (!pendingBroadcasts.contains(line)) {
            pendingBroadcasts.addLast(line);
            QT.FLAG_LOGGER.info("Queued broadcast ({} ticks until next slot): {} {}", now - lastBroadcastAt, key, args);
        } else {
            QT.FLAG_LOGGER.info("Dropped duplicate queued broadcast: {} {}", key, args);
        }
    }

    /**
     * Release one queued broadcast once the spacing interval is free. Ticked
     * every flag tick by {@link TownFlagTicker}.
     */
    public void flushBroadcasts() {
        if (pendingBroadcasts.isEmpty()) {
            return;
        }
        if (level == null) {
            pendingBroadcasts.clear();
            return;
        }
        long now = level.getGameTime();
        if (lastBroadcastAt == Long.MIN_VALUE || now - lastBroadcastAt >= BROADCAST_SPACING_TICKS) {
            lastBroadcastAt = now;
            sendNow(pendingBroadcasts.pollFirst());
        }
    }

    private void sendNow(Component line) {
        for (ServerPlayer p : level.getServer()
                                   .getPlayerList()
                                   .getPlayers()) {
            p.displayClientMessage(line, false);
        }
    }

    void broadcastTutorialToast(String titleKey, String descriptionKey) {
        if (level == null) {
            QT.FLAG_LOGGER.debug("Skipping tutorial toast before TownMessages was initialized: {} {}", titleKey, descriptionKey);
            return;
        }
        QT.FLAG_LOGGER.info("Broadcasting tutorial toast: {} {}", titleKey, descriptionKey);
        for (ServerPlayer p : level.getServer().getPlayerList().getPlayers()) {
            QuestownNetwork.sendToPlayer(p,
                    new ShowTutorialToastMessage(titleKey, descriptionKey));
        }
    }

    void sendTutorialToast(ServerPlayer p, String titleKey, String descriptionKey) {
        QT.FLAG_LOGGER.info("Sending tutorial toast to {}: {} {}", p.getName().getString(), titleKey, descriptionKey);
        QuestownNetwork.sendToPlayer(p, new ShowTutorialToastMessage(titleKey, descriptionKey));
    }

    public void roomRecipeCreated(
            MCRoom roomDoorPos,
            Optional<ResourceLocation> match
    ) {
        broadcastMessage(
                "messages.building.recipe_created",
                RoomRecipes.getName(match),
                roomDoorPos.getDoorPos()
                           .getUIString()
        );
    }

    public void roomRecipeChanged(
            ResourceLocation oldMatchID,
            ResourceLocation newMatchID,
            MCRoom newRoom
    ) {
        broadcastMessage(
                "messages.building.room_changed",
                Compat.translatable("room." + oldMatchID.getPath()),
                Compat.translatable("room." + newMatchID.getPath()),
                newRoom.getDoorPos()
                       .getUIString()
        );
    }

    public void roomRecipeDestroyed(
            MCRoom roomDoorPos,
            @Nullable ResourceLocation oldRecipeId
    ) {
        broadcastMessage(
                "messages.building.room_destroyed",
                RoomRecipes.getName(Optional.ofNullable(oldRecipeId)),
                roomDoorPos.getDoorPos()
                           .getUIString()
        );
    }

    public void questCompleted(MCQuest quest) {
        Component name = RoomRecipes.getName(quest.getWantedId());
        if (quest.getType() == Quest.QuestType.ITEM) {
            name = Compat.translatable("menu.common.quantity", Compat.getItemName(quest.getWantedId()), quest.getCountNeeded());
        }
        if (quest.getType() == Quest.QuestType.LOOT) {
            name = Compat.translatable(ca.bradj.questown.gui.UIQuest.lootNameKey(quest.getWantedId()));
        }
        if (quest.getType() == Quest.QuestType.JOB_UNLOCK) {
            name = Compat.translatable("questown.menu.quests.job_unlock", JobID.fromRL(quest.getWantedId()).rootId());
        }
        broadcastMessage(
                "messages.town_flag.quest_completed",
                name
        );
    }

    public void questLost(MCQuest quest) {
        Component name = RoomRecipes.getName(quest.getWantedId());
        if (quest.getType() == Quest.QuestType.ITEM) {
            name = Compat.translatable("menu.common.quantity", Compat.getItemName(quest.getWantedId()), quest.getCountNeeded());
        }
        if (quest.getType() == Quest.QuestType.LOOT) {
            name = Compat.translatable(ca.bradj.questown.gui.UIQuest.lootNameKey(quest.getWantedId()));
        }
        if (quest.getType() == Quest.QuestType.JOB_UNLOCK) {
            name = Compat.translatable("questown.menu.quests.job_unlock", JobID.fromRL(quest.getWantedId()).rootId());
        }
        broadcastMessage(
                "messages.town_flag.quest_lost",
                name
        );
    }

    public void jobChanged(
            JobID jobID,
            UUID visitorUUID
    ) {
        jobChanged(jobID, UtilClean.truncateMiddle(visitorUUID));
    }

    /** Same broadcast, with an explicit display label ("Villager N"). */
    public void jobChanged(
            JobID jobID,
            String villagerLabel
    ) {
        broadcastMessage("messages.jobs.changed", jobID.toNiceString(), villagerLabel);
    }

    public void startDebugFailed() {
        broadcastMessage("First you must enabled debug mode on the flag via the /qtdebug <POS> command");
    }

    public void debugToggled(boolean debugMode) {
        broadcastMessage("message.debug_mode", debugMode ? "enabled" : "disabled");
    }

    public void batchRemoved() {
        broadcastMessage("messages.town_flag.quest_batch_removed_1");
        broadcastMessage("messages.town_flag.quest_batch_removed_2");
    }

    public void roomCreated(
            @Nullable ResourceLocation recipe,
            Position doorPos
    ) {
        if (recipe == null) {
            broadcastMessage(
                    "messages.building.room_created",
                    doorPos.getUIString()
            );
        } else {
            broadcastMessage(
                    "messages.building.specific_room_created",
                    RoomRecipes.getName(recipe),
                    doorPos.getUIString()
            );
        }
    }

    public void roomSizeChanged(
            @Nullable ResourceLocation recipe,
            Position doorPos
    ) {
        broadcastMessage(
                "messages.building.room_size_changed",
                RoomRecipes.getName(Optional.ofNullable(recipe)),
                doorPos.getUIString()
        );
    }

    public void roomDestroyed(
            @Nullable ResourceLocation recipe,
            Position doorPos
    ) {
        broadcastMessage(
                "messages.building.room_destroyed",
                RoomRecipes.getName(Optional.ofNullable(recipe)),
                doorPos.getUIString()
        );
    }
}

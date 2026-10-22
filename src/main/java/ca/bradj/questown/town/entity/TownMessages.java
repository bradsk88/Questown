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
    private final BroadcastPacer<Component> pacer = new BroadcastPacer<>(BROADCAST_SPACING_TICKS);

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
        switch (pacer.offer(line, level.getGameTime(), this::sendNow)) {
            case SENT -> QT.FLAG_LOGGER.info("Broadcasting message: {} {}", key, args);
            case QUEUED -> QT.FLAG_LOGGER.info("Queued broadcast (waiting for a free slot): {} {}", key, args);
            case DROPPED -> QT.FLAG_LOGGER.info("Dropped duplicate broadcast: {} {}", key, args);
        }
    }

    /**
     * Release one queued broadcast once the spacing interval is free. Ticked
     * every flag tick by {@link TownFlagTicker}.
     */
    public void flushBroadcasts() {
        if (pacer.isEmpty()) {
            return;
        }
        if (level == null) {
            pacer.clear();
            return;
        }
        pacer.flush(level.getGameTime(), this::sendNow);
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
        broadcastMessage(
                "messages.town_flag.quest_completed",
                questName(quest.getType(), quest.getWantedId(), quest.getCountNeeded())
        );
    }

    /**
     * The quest's name for chat, worded like its quest card ({@code UIQuest.getName}). Job quests
     * name the job by its translated root ({@code jobs.<root>}); before this a job-change quest fell
     * through to the room lookup and read as a raw key ("room.hunter_qt_sword").
     */
    static Component questName(
            Quest.QuestType type,
            ResourceLocation wantedId,
            int countNeeded
    ) {
        return switch (type) {
            case ITEM -> Compat.translatable("menu.common.quantity", Compat.getItemName(wantedId), countNeeded);
            case LOOT -> Compat.translatable(ca.bradj.questown.gui.UIQuest.lootNameKey(wantedId));
            case JOB_CHANGE -> Compat.translatable("questown.menu.quests.job_change", jobName(wantedId));
            case JOB_UNLOCK -> Compat.translatable("questown.menu.quests.job_unlock", jobName(wantedId));
            default -> RoomRecipes.getName(wantedId);
        };
    }

    private static Component jobName(ResourceLocation jobQuestId) {
        return Compat.translatable("jobs." + JobID.fromRL(jobQuestId).rootId());
    }

    public void questLost(MCQuest quest) {
        broadcastMessage(
                "messages.town_flag.quest_lost",
                questName(quest.getType(), quest.getWantedId(), quest.getCountNeeded())
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
        // The profession's name, not the raw id ("cook:simple_furnace_food", 2026-10-17 playtest).
        broadcastMessage("messages.jobs.changed", ca.bradj.questown.jobs.Jobs.getRootNameComponent(jobID), villagerLabel);
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

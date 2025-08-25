package ca.bradj.questown.town;

import ca.bradj.questown.QT;
import ca.bradj.questown.core.UtilClean;
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
    private ServerLevel level;

    public void initialize(ServerLevel t) {
        this.level = t;
    }

    void broadcastMessage(
            String key,
            Object... args
    ) {
        QT.FLAG_LOGGER.info("Broadcasting message: {} {}", key, args);
        for (ServerPlayer p : level.getServer()
                                   .getPlayerList()
                                   .getPlayers()) {
            p.displayClientMessage(Compat.translatable(key, args), false);
        }
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
        broadcastMessage(
                "messages.town_flag.quest_lost",
                name
        );
    }

    public void jobChanged(
            JobID jobID,
            UUID visitorUUID
    ) {
        broadcastMessage("messages.jobs.changed", jobID.toNiceString(), UtilClean.truncateMiddle(visitorUUID));
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

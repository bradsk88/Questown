package ca.bradj.questown.town;

import ca.bradj.questown.QT;
import ca.bradj.questown.jobs.JobID;
import ca.bradj.questown.logic.RoomRecipes;
import ca.bradj.questown.mc.Compat;
import ca.bradj.questown.town.quests.MCQuest;
import ca.bradj.roomrecipes.adapter.RoomRecipeMatch;
import ca.bradj.roomrecipes.core.space.Position;
import ca.bradj.roomrecipes.serialization.MCRoom;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

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

    public void roomRecipeCreated(MCRoom roomDoorPos, RoomRecipeMatch<MCRoom> match) {
        broadcastMessage(
                "messages.building.recipe_created",
                RoomRecipes.getName(match.getRecipeID()),
                roomDoorPos.getDoorPos()
                        .getUIString()
        );
    }

    public void roomRecipeChanged(RoomRecipeMatch<?> oldMatch, RoomRecipeMatch<?> newMatch, MCRoom newRoom) {
        ResourceLocation oldMatchID = oldMatch.getRecipeID();
        ResourceLocation newMatchID = newMatch.getRecipeID();
        broadcastMessage(
                "messages.building.room_changed",
                Compat.translatable("room." + oldMatchID.getPath()),
                Compat.translatable("room." + newMatchID.getPath()),
                newRoom.getDoorPos()
                        .getUIString()
        );
    }

    public void roomRecipeDestroyed(MCRoom roomDoorPos, RoomRecipeMatch<?> oldRecipeId) {
        broadcastMessage(
                "messages.building.room_destroyed",
                Compat.translatable("room." + oldRecipeId.getRecipeID()
                        .getPath()),
                roomDoorPos.getDoorPos()
                        .getUIString()
        );
    }

    public void questCompleted(MCQuest quest) {
        broadcastMessage(
                "messages.town_flag.quest_completed",
                RoomRecipes.getName(quest.getWantedId())
        );
    }

    public void questLost(MCQuest quest) {
        broadcastMessage(
                "messages.town_flag.quest_lost",
                RoomRecipes.getName(quest.getWantedId())
        );
    }

    public void jobChanged(JobID jobID, UUID visitorUUID) {
        broadcastMessage("messages.jobs.changed", jobID.toNiceString(), visitorUUID);
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

    public void roomCreated(Optional<RoomRecipeMatch<MCRoom>> recipe, Position doorPos) {
        broadcastMessage(
                "messages.building.room_created",
                RoomRecipes.getName(recipe.map(RoomRecipeMatch::getRecipeID)),
                doorPos.getUIString()
        );
    }

    public void roomSizeChanged(Optional<RoomRecipeMatch<MCRoom>> recipe, Position doorPos) {
        broadcastMessage(
                "messages.building.room_size_changed",
                RoomRecipes.getName(recipe.map(RoomRecipeMatch::getRecipeID)),
                doorPos.getUIString()
        );
    }

    public void roomDestroyed(Optional<RoomRecipeMatch<MCRoom>> recipe, Position doorPos) {
        broadcastMessage(
                "messages.building.room_destroyed",
                RoomRecipes.getName(recipe.map(RoomRecipeMatch::getRecipeID)),
                doorPos.getUIString()
        );
    }
}

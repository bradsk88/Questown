package ca.bradj.questown.gui;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;

import java.util.Collection;

public interface FlagTabsEmbedding {
    Collection<String> getEnabledTabs();

    record FlagInfo(
            BlockPos flagPos,
            boolean showVillagersTab,
            boolean showQuestsTab,
            boolean showEconTab,
            boolean showBlockOfProgressTab,
            // The quests tab's red dot: open quests the player has not yet seen on the tab (dev
            // playtest 2026-10-30). Not "any incomplete quest": that is the flag's "!" bubble.
            boolean questsTabAttention,
            boolean bopFull,
            boolean bonusFoodAvailable,
            boolean villagersTabAttention,
            boolean bopTabAttention
    ) {
        /** The villagers tab wants attention once it is shown and no player has opened it yet. */
        public static boolean villagersTabAttention(boolean showVillagersTab, boolean villagersTabSeen) {
            return showVillagersTab && !villagersTabSeen;
        }

        public static FlagInfo dumb(BlockPos flagPos, boolean showBOP, boolean bopFull) {
            return new FlagInfo(flagPos, true, true, true, showBOP, false, bopFull, false, false, true);
        }

        public static FlagInfo withQuestNotification(BlockPos flagPos, boolean showBOP, boolean questsTabAttention, boolean bopFull) {
            return new FlagInfo(flagPos, true, true, true, showBOP, questsTabAttention, bopFull, false, false, true);
        }

        public static FlagInfo read(FriendlyByteBuf buf) {
            return new FlagInfo(
                    buf.readBlockPos(),
                    buf.readBoolean(),
                    buf.readBoolean(),
                    buf.readBoolean(),
                    buf.readBoolean(),
                    buf.readBoolean(),
                    buf.readBoolean(),
                    buf.readBoolean(),
                    buf.readBoolean(),
                    buf.readBoolean()
            );
        }

        public void write(FriendlyByteBuf data) {
            data.writeBlockPos(flagPos);
            data.writeBoolean(showVillagersTab);
            data.writeBoolean(showQuestsTab);
            data.writeBoolean(showEconTab);
            data.writeBoolean(showBlockOfProgressTab);
            data.writeBoolean(questsTabAttention);
            data.writeBoolean(bopFull);
            data.writeBoolean(bonusFoodAvailable);
            data.writeBoolean(villagersTabAttention);
            data.writeBoolean(bopTabAttention);
        }
    }

    FlagInfo getFlagInfo();
}

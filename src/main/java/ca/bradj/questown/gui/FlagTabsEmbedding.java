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
            boolean hasIncompleteQuests,
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

        public static FlagInfo withQuestNotification(BlockPos flagPos, boolean showBOP, boolean hasIncompleteQuests, boolean bopFull) {
            return new FlagInfo(flagPos, true, true, true, showBOP, hasIncompleteQuests, bopFull, false, false, true);
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
            data.writeBoolean(hasIncompleteQuests);
            data.writeBoolean(bopFull);
            data.writeBoolean(bonusFoodAvailable);
            data.writeBoolean(villagersTabAttention);
            data.writeBoolean(bopTabAttention);
        }
    }

    FlagInfo getFlagInfo();
}

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
            boolean showBlockOfProgressTab
    ) {
        public static FlagInfo dumb(BlockPos flagPos, boolean showBOP) {
            // Assumes that we should always show all tabs (except BOP)
            // TODO: Player onboarding might be improved by hiding some tabs initially
            return new FlagInfo(flagPos, true, true, true, showBOP);
        }

        public static FlagInfo read(FriendlyByteBuf buf) {
            return new FlagInfo(
                    buf.readBlockPos(),
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
        }
    }

    FlagInfo getFlagInfo();
}

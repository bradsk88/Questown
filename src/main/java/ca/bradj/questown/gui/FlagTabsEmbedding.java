package ca.bradj.questown.gui;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;

import java.util.Collection;

public interface FlagTabsEmbedding {
    Collection<String> getEnabledTabs();

    record FlagInfo(BlockPos flagPos, boolean showBlockOfProgressTab) {
        public static FlagInfo read(FriendlyByteBuf buf) {
            return new FlagInfo(buf.readBlockPos(), buf.readBoolean());
        }

        public void write(FriendlyByteBuf data) {
            data.writeBlockPos(flagPos);
            data.writeBoolean(showBlockOfProgressTab);
        }
    }

    FlagInfo getFlagInfo();
}

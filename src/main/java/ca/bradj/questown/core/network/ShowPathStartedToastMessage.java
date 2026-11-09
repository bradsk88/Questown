package ca.bradj.questown.core.network;

import ca.bradj.questown.QT;
import ca.bradj.questown.gui.ClientAccess;
import ca.bradj.questown.loader.Channel;
import ca.bradj.questown.loader.Loader;
import ca.bradj.questown.mc.Compat;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public record ShowPathStartedToastMessage(
        String nameKey, ResourceLocation iconItem
) {

    public static void encode(ShowPathStartedToastMessage msg, FriendlyByteBuf buffer) {
        buffer.writeUtf(msg.nameKey());
        buffer.writeResourceLocation(msg.iconItem());
    }

    public static ShowPathStartedToastMessage decode(FriendlyByteBuf buffer) {
        String nameKey = buffer.readUtf();
        ResourceLocation iconItem = buffer.readResourceLocation();
        return new ShowPathStartedToastMessage(nameKey, iconItem);
    }

    public void handle(
            Channel.Context ctx
    ) {
        ctx.enqueueWork(() -> Loader.runClient(() -> {
            Item icon = Loader.itemFromId(iconItem);
            ClientAccess.showPathStartedToast(
                    Compat.translatable(nameKey),
                    icon == null ? ItemStack.EMPTY : icon.getDefaultInstance()
            );
        })).exceptionally(ShowPathStartedToastMessage::logError);
    }

    private static Void logError(Throwable ex) {
        QT.GUI_LOGGER.error("Failed to show path started toast", ex);
        return null;
    }
}

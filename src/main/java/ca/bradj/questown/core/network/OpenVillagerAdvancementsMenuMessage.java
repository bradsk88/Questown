package ca.bradj.questown.core.network;

import ca.bradj.questown.QT;
import ca.bradj.questown.gui.villager.advancements.VillagerAdvancementsScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record OpenVillagerAdvancementsMenuMessage() {

    public static void encode(OpenVillagerAdvancementsMenuMessage msg, FriendlyByteBuf buffer) {
    }

    public static OpenVillagerAdvancementsMenuMessage decode(FriendlyByteBuf buffer) {
        return new OpenVillagerAdvancementsMenuMessage();
    }


    public void handle(
            Supplier<NetworkEvent.Context> ctx
    ) {
        ctx.get().enqueueWork(() -> {
            Minecraft.getInstance().setScreen(new VillagerAdvancementsScreen());
        }).exceptionally(OpenVillagerAdvancementsMenuMessage::logError);
        ctx.get().setPacketHandled(true);

    }

    private static Void logError(Throwable ex) {
        QT.GUI_LOGGER.error("Failed to open villager advancements screen", ex);
        return null;
    }
}

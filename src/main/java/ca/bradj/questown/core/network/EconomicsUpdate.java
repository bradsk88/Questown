package ca.bradj.questown.core.network;

import ca.bradj.questown.QT;
import ca.bradj.questown.gui.ItemEconomicsData;
import ca.bradj.questown.gui.TownEconomicsScreen;
import ca.bradj.questown.gui.VillagerEconomicsScreen;
import com.google.common.collect.ImmutableList;
import ca.bradj.questown.loader.Channel;
import ca.bradj.questown.loader.Loader;
import net.minecraft.network.RegistryFriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;

public record EconomicsUpdate(
        ImmutableList<ItemEconomicsData> data
) {

    public static void encode(
            EconomicsUpdate msg,
            RegistryFriendlyByteBuf buffer
    ) {
        buffer.writeCollection(
                msg.data, (b, i) -> {
                    b.writeUtf(i.ingredientKey());
                    b.writeInt(i.timesNeeded());
                }
        );
    }

    public static EconomicsUpdate decode(RegistryFriendlyByteBuf buffer) {
        List<ItemEconomicsData> l = buffer.readCollection(ArrayList::new, b -> new ItemEconomicsData(b.readUtf(), b.readInt()));
        return new EconomicsUpdate(ImmutableList.copyOf(l));
    }

    public void handle(
            Channel.Context ctx
    ) {
        ctx.enqueueWork(() -> Loader.runClient(() -> {
            VillagerEconomicsScreen.lastUpdate = this;
            TownEconomicsScreen.lastUpdate = this;
        })).exceptionally(EconomicsUpdate::logError);

    }

    private static Void logError(Throwable ex) {
        String name = EconomicsUpdate.class.getName();
        QT.GUI_LOGGER.error("Failed to send {} data to player", name, ex);
        return null;
    }
}

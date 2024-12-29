package ca.bradj.questown.core.network;

import ca.bradj.questown.QT;
import ca.bradj.questown.gui.ItemEconomicsData;
import ca.bradj.questown.gui.TownEconomicsScreen;
import ca.bradj.questown.gui.VillagerEconomicsScreen;
import com.google.common.collect.ImmutableList;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public record EconomicsUpdate(
        ImmutableList<ItemEconomicsData> data
) {

    public static void encode(
            EconomicsUpdate msg,
            FriendlyByteBuf buffer
    ) {
        buffer.writeCollection(
                msg.data, (b, i) -> {
                    b.writeUtf(i.ingredientKey());
                    b.writeInt(i.timesNeeded());
                }
        );
    }

    public static EconomicsUpdate decode(FriendlyByteBuf buffer) {
        List<ItemEconomicsData> l = buffer.readCollection(ArrayList::new, b -> new ItemEconomicsData(b.readUtf(), b.readInt()));
        return new EconomicsUpdate(ImmutableList.copyOf(l));
    }

    public void handle(
            Supplier<NetworkEvent.Context> ctx
    ) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(
                Dist.CLIENT,
                () -> () -> {
                    VillagerEconomicsScreen.lastUpdate = this;
                    TownEconomicsScreen.lastUpdate = this;
                }
        )).exceptionally(EconomicsUpdate::logError);
        ctx.get().setPacketHandled(true);

    }

    private static Void logError(Throwable ex) {
        String name = EconomicsUpdate.class.getName();
        QT.GUI_LOGGER.error("Failed to send {} data to player", name, ex);
        return null;
    }
}

package ca.bradj.questown.core.network;

import ca.bradj.questown.QT;
import ca.bradj.questown.gui.ClientJobWantedResources;
import com.google.common.collect.ImmutableList;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

public record JobWantedIngredientsMessage(
        ImmutableList<Ingredient> ingredients
) {

    public static void encode(
            JobWantedIngredientsMessage msg,
            FriendlyByteBuf buffer
    ) {
        buffer.writeInt(msg.ingredients.size());
        msg.ingredients.forEach(i -> {
            JsonElement json = i.toJson();
            String s = json.toString();
            buffer.writeUtf(s);
        });
    }

    public static JobWantedIngredientsMessage decode(FriendlyByteBuf buffer) {
        ImmutableList.Builder<Ingredient> b = ImmutableList.builder();
        int num = buffer.readInt();
        for (int i = 0; i < num; i++) {
            JsonParser p = new JsonParser();
            JsonElement j = p.parse(buffer.readUtf());
            b.add(Ingredient.fromJson(j));
        }
        return new JobWantedIngredientsMessage(b.build());
    }

    public void handle(
            Supplier<NetworkEvent.Context> ctx
    ) {
        final AtomicBoolean success = new AtomicBoolean(false);
        ctx.get().enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(
                    Dist.CLIENT,
                    () -> () -> {
                        ClientJobWantedResources.wantedIngredients = ingredients;
                        success.set(true);
                    }
            );
        }).exceptionally(JobWantedIngredientsMessage::logError);
        ctx.get().setPacketHandled(true);

    }

    private static Void logError(Throwable ex) {
        QT.GUI_LOGGER.error("Failed to send wanted ingredients to player", ex);
        return null;
    }
}

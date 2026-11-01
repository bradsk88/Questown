package ca.bradj.questown.core.network;

import ca.bradj.questown.QT;
import ca.bradj.questown.gui.ClientJobWantedResources;
import com.google.common.collect.ImmutableList;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import ca.bradj.questown.loader.Channel;
import ca.bradj.questown.loader.Loader;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.concurrent.atomic.AtomicBoolean;

public record JobWantedIngredientsMessage(
        ImmutableList<Ingredient> ingredients
) {

    public static void encode(
            JobWantedIngredientsMessage msg,
            FriendlyByteBuf buffer
    ) {
        buffer.writeInt(msg.ingredients.size());
        msg.ingredients.forEach(i -> {
            com.google.gson.JsonElement json = ca.bradj.questown.mc.Compat.ingredientToJson(i);
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
            b.add(ca.bradj.questown.mc.Compat.ingredientFromJson(j));
        }
        return new JobWantedIngredientsMessage(b.build());
    }

    public void handle(
            Channel.Context ctx
    ) {
        final AtomicBoolean success = new AtomicBoolean(false);
        ctx.enqueueWork(() -> Loader.runClient(() -> {
            ClientJobWantedResources.wantedIngredients = ingredients;
            success.set(true);
        })).exceptionally(JobWantedIngredientsMessage::logError);

    }

    private static Void logError(Throwable ex) {
        QT.GUI_LOGGER.error("Failed to send wanted ingredients to player", ex);
        return null;
    }
}

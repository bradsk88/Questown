package ca.bradj.questown.core.network;

import ca.bradj.questown.QT;
import ca.bradj.questown.gui.ClientJobWantedResources;
import com.google.common.collect.ImmutableList;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import ca.bradj.questown.loader.Channel;
import ca.bradj.questown.loader.Loader;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.concurrent.atomic.AtomicBoolean;

public record JobWantedIngredientsMessage(
        ImmutableList<Ingredient> ingredients
) {

    public static void encode(
            JobWantedIngredientsMessage msg,
            RegistryFriendlyByteBuf buffer
    ) {
        buffer.writeInt(msg.ingredients.size());
        msg.ingredients.forEach(i -> {
            com.google.gson.JsonElement json = net.minecraft.world.item.crafting.Ingredient.CODEC_NONEMPTY
                    .encode(i, com.mojang.serialization.JsonOps.INSTANCE, com.mojang.serialization.JsonOps.INSTANCE.empty()).getOrThrow();
            String s = json.toString();
            buffer.writeUtf(s);
        });
    }

    public static JobWantedIngredientsMessage decode(RegistryFriendlyByteBuf buffer) {
        ImmutableList.Builder<Ingredient> b = ImmutableList.builder();
        int num = buffer.readInt();
        for (int i = 0; i < num; i++) {
            JsonParser p = new JsonParser();
            JsonElement j = p.parse(buffer.readUtf());
            b.add(net.minecraft.world.item.crafting.Ingredient.CODEC_NONEMPTY.parse(com.mojang.serialization.JsonOps.INSTANCE, j).getOrThrow());
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

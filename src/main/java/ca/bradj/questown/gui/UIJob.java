package ca.bradj.questown.gui;

import com.google.common.collect.ImmutableList;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.crafting.Ingredient;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.UUID;

public record UIJob(
        ImmutableList<UUID> villagersWhoCanDoJob,
        ImmutableList<Ingredient> ingredients,
        ImmutableList<Ingredient> tools
) {
    public static UIJob fromNetwork(FriendlyByteBuf buf) {
        List<UUID> vs = buf.readList(FriendlyByteBuf::readUUID);
        List<String> in = buf.readList(FriendlyByteBuf::readUtf);
        List<String> tl = buf.readList(FriendlyByteBuf::readUtf);
        return fromBufferData(in, tl, vs);
    }

    public static void toNetwork(
            FriendlyByteBuf buf,
            UIJob uiJob
    ) {
        List<String> ingIDs = uiJob.ingredients().stream().map(Ingredients::toString).toList();
        List<String> toolIDs = uiJob.tools().stream().map(Ingredients::toString).toList();

        buf.writeCollection(uiJob.villagersWhoCanDoJob(), FriendlyByteBuf::writeUUID);
        buf.writeCollection(ingIDs, FriendlyByteBuf::writeUtf);
        buf.writeCollection(toolIDs, FriendlyByteBuf::writeUtf);
    }

    private static @NotNull UIJob fromBufferData(
            List<String> in,
            List<String> tl,
            List<UUID> vs
    ) {
        ImmutableList.Builder<Ingredient> bIngredients = ImmutableList.builder();
        ImmutableList.Builder<Ingredient> bTools = ImmutableList.builder();

        for (String s : in) {
            bIngredients.add(Ingredients.fromString(s));
        }
        for (String s : tl) {
            bTools.add(Ingredients.fromString(s));
        }
        return new UIJob(ImmutableList.copyOf(vs), bIngredients.build(), bTools.build());
    }
}

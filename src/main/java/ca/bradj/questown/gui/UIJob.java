package ca.bradj.questown.gui;

import ca.bradj.questown.core.network.NetworkCompat;
import ca.bradj.questown.jobs.JobID;
import com.google.common.collect.ImmutableList;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.UUID;

public record UIJob(
        JobID jobId,
        ImmutableList<UUID> villagersWhoCanDoJob,
        ImmutableList<Ingredient> ingredients,
        ImmutableList<Ingredient> tools,
        ResourceLocation roomNameTranslationKey,
        ImmutableList<Ingredient> roomRecipe,
        ImmutableList<ItemStack> result
) {
    public static UIJob fromNetwork(RegistryFriendlyByteBuf buf) {
        JobID jobId = NetworkCompat.fromNetworkJobID(buf);
        List<UUID> vs = buf.readList(b -> b.readUUID());
        List<String> in = buf.readList(b -> b.readUtf());
        List<String> tl = buf.readList(b -> b.readUtf());
        ResourceLocation rrName = buf.readResourceLocation();
        List<String> rr = buf.readList(b -> b.readUtf());
        List<ItemStack> result = buf.readList(b -> net.minecraft.world.item.ItemStack.STREAM_CODEC.decode((RegistryFriendlyByteBuf) b));
        return fromBufferData(jobId, in, tl, vs, rrName, rr, result);
    }

    public static void toNetwork(
            RegistryFriendlyByteBuf buf,
            UIJob uiJob
    ) {
        NetworkCompat.toNetwork(buf, uiJob.jobId());
        List<String> ingIDs = uiJob.ingredients().stream().map(Ingredients::toString).toList();
        List<String> toolIDs = uiJob.tools().stream().map(Ingredients::toString).toList();
        List<String> roomRecipe = uiJob.roomRecipe().stream().map(Ingredients::toString).toList();

        buf.writeCollection(uiJob.villagersWhoCanDoJob(), (b, v) -> b.writeUUID(v));
        buf.writeCollection(ingIDs, (b, v) -> b.writeUtf(v));
        buf.writeCollection(toolIDs, (b, v) -> b.writeUtf(v));
        buf.writeUtf(uiJob.roomNameTranslationKey().toString());
        buf.writeCollection(roomRecipe, (b, v) -> b.writeUtf(v));
        buf.writeCollection(uiJob.result(), (b, v) -> net.minecraft.world.item.ItemStack.STREAM_CODEC.encode((RegistryFriendlyByteBuf) b, v));
    }

    private static @NotNull UIJob fromBufferData(
            JobID jobId,
            List<String> in,
            List<String> tl,
            List<UUID> vs,
            ResourceLocation rrNameKey,
            List<String> rr,
            List<ItemStack> result
    ) {
        ImmutableList.Builder<Ingredient> bIngredients = ImmutableList.builder();
        ImmutableList.Builder<Ingredient> bTools = ImmutableList.builder();
        ImmutableList.Builder<Ingredient> bRoom = ImmutableList.builder();

        for (String s : in) {
            bIngredients.add(Ingredients.fromString(s));
        }
        for (String s : tl) {
            bTools.add(Ingredients.fromString(s));
        }
        for (String s : rr) {
            bRoom.add(Ingredients.fromString(s));
        }

        return new UIJob(
                jobId,
                ImmutableList.copyOf(vs),
                bIngredients.build(),
                bTools.build(),
                rrNameKey,
                bRoom.build(),
                ImmutableList.copyOf(result)
        );
    }
}

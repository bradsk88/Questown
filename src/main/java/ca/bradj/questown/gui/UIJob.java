package ca.bradj.questown.gui;

import ca.bradj.questown.core.network.NetworkCompat;
import ca.bradj.questown.jobs.JobID;
import com.google.common.collect.ImmutableList;
import net.minecraft.network.FriendlyByteBuf;
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
        ItemStack result
) {
    public static UIJob fromNetwork(FriendlyByteBuf buf) {
        JobID jobId = NetworkCompat.fromNetworkJobID(buf);
        List<UUID> vs = buf.readList(FriendlyByteBuf::readUUID);
        List<String> in = buf.readList(FriendlyByteBuf::readUtf);
        List<String> tl = buf.readList(FriendlyByteBuf::readUtf);
        ResourceLocation rrName = buf.readResourceLocation();
        List<String> rr = buf.readList(FriendlyByteBuf::readUtf);
        ItemStack result = buf.readItem();
        return fromBufferData(jobId, in, tl, vs, rrName, rr, result);
    }

    public static void toNetwork(
            FriendlyByteBuf buf,
            UIJob uiJob
    ) {
        NetworkCompat.toNetwork(buf, uiJob.jobId());
        List<String> ingIDs = uiJob.ingredients().stream().map(Ingredients::toString).toList();
        List<String> toolIDs = uiJob.tools().stream().map(Ingredients::toString).toList();
        List<String> roomRecipe = uiJob.roomRecipe().stream().map(Ingredients::toString).toList();

        buf.writeCollection(uiJob.villagersWhoCanDoJob(), FriendlyByteBuf::writeUUID);
        buf.writeCollection(ingIDs, FriendlyByteBuf::writeUtf);
        buf.writeCollection(toolIDs, FriendlyByteBuf::writeUtf);
        buf.writeUtf(uiJob.roomNameTranslationKey().toString());
        buf.writeCollection(roomRecipe, FriendlyByteBuf::writeUtf);
        buf.writeItem(uiJob.result());
    }

    private static @NotNull UIJob fromBufferData(
            JobID jobId,
            List<String> in,
            List<String> tl,
            List<UUID> vs,
            ResourceLocation rrNameKey,
            List<String> rr,
            ItemStack result
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
                result
        );
    }
}

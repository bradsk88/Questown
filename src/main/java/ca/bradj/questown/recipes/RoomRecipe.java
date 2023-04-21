package ca.bradj.questown.recipes;

import ca.bradj.questown.Questown;
import com.google.common.collect.ImmutableMultiset;
import com.google.common.primitives.Ints;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.core.NonNullList;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.ForgeRegistryEntry;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

public class RoomRecipe implements Recipe<Container>, Comparable<RoomRecipe> {
    @Override
    public String toString() {
        return "RoomRecipe{" +
                "id=" + id +
                ", recipeItems=" + recipeItems +
                '}';
    }

    private final ResourceLocation id;
    private final NonNullList<Ingredient> recipeItems;

    public RoomRecipe(
            ResourceLocation id,
            NonNullList<Ingredient> recipeItems
    ) {
        this.id = id;
        this.recipeItems = recipeItems;
    }

    @Override
    public boolean matches(
            Container inv,
            Level p_77569_2_
    ) {
        List<Ingredient> found = new ArrayList<>();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack item = inv.getItem(i);
            Ingredient foundIng = null;
            for (Ingredient ing : recipeItems) {
                if (ing.test(item)) {
                    foundIng = ing;
                    break;
                }
            }
            if (foundIng != null) {
                found.add(foundIng);
            }
        }
        ImmutableMultiset<Ingredient> foundMS = ImmutableMultiset.copyOf(found);
        ImmutableMultiset<Ingredient> recipeMS = ImmutableMultiset.copyOf(recipeItems);
        return foundMS.containsAll(recipeMS);
    }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        return this.recipeItems;
    }

    @Override
    public @NotNull ItemStack getResultItem() {
        return ItemStack.EMPTY;
    }

    @Override
    public @NotNull ItemStack assemble(Container p_77572_1_) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean canCraftInDimensions(
            int p_43999_,
            int p_44000_
    ) {
        return true;
    }

    @Override
    public @NotNull ResourceLocation getId() {
        return this.id;
    }

    @Override
    public @NotNull RecipeSerializer<?> getSerializer() {
        return RecipesInit.ROOM_SERIALIZER.get();
    }

    @Override
    public @NotNull RecipeType<?> getType() {
        return RecipesInit.ROOM;
    }

    @Override
    public int compareTo(@NotNull RoomRecipe roomRecipe) {
        return Ints.compare(getIngredients().size(), roomRecipe.getIngredients().size());
    }

    public static class Serializer extends ForgeRegistryEntry<RecipeSerializer<?>> implements RecipeSerializer<RoomRecipe> {

        @Override
        public @NotNull RoomRecipe fromJson(
                @NotNull ResourceLocation recipeId,
                @NotNull JsonObject json
        ) {
            JsonArray ingredients = GsonHelper.getAsJsonArray(json, "ingredients");
            NonNullList<Ingredient> inputs = NonNullList.withSize(ingredients.size(), Ingredient.EMPTY);

            for (int i = 0; i < ingredients.size(); i++) {
                inputs.set(i, Ingredient.fromJson(ingredients.get(i)));
            }

            return new RoomRecipe(recipeId, inputs);
        }

        @Nullable
        @Override
        public RoomRecipe fromNetwork(
                @NotNull ResourceLocation recipeId,
                FriendlyByteBuf buffer
        ) {
            int rSize = buffer.readInt();
            NonNullList<Ingredient> inputs = NonNullList.withSize(rSize, Ingredient.EMPTY);
            for (int i = 0; i < rSize; i++) {
                inputs.set(i, Ingredient.fromNetwork(buffer));
            }
            return new RoomRecipe(recipeId, inputs);
        }

        @Override
        public void toNetwork(
                FriendlyByteBuf buffer,
                RoomRecipe recipe
        ) {
            buffer.writeInt(recipe.getIngredients().size());
            for (Ingredient ing : recipe.getIngredients()) {
                ing.toNetwork(buffer);
            }
            buffer.writeItem(recipe.getResultItem());
        }
    }

    public static class Type implements RecipeType<RoomRecipe> {

        public static final Type INSTANCE = new Type();
        public static final ResourceLocation ID = new ResourceLocation(Questown.MODID, "room");

    }

}

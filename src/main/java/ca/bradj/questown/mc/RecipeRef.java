package ca.bradj.questown.mc;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

/**
 * Recipe lookups by id. On 1.20.2+ a recipe's id lives on its holder, not the recipe, so leaves
 * look recipes up through these helpers instead of naming the holder type.
 */
public final class RecipeRef {

    private RecipeRef() {}

    /** Every recipe of the given type. */
    public static
    <IN extends net.minecraft.world.Container,
     T extends net.minecraft.world.item.crafting.Recipe<IN>>
    List<T> recipesOf(
            RecipeManager rm,
            RecipeType<T> type
    ) {
        return rm.getAllRecipesFor(type)
                 .stream()
                 .map(net.minecraft.world.item.crafting.RecipeHolder::value)
                 .collect(Collectors.toList());
    }

    /** The recipe value whose holder-id matches, or empty. */
    public static
    <IN extends net.minecraft.world.Container,
     T extends net.minecraft.world.item.crafting.Recipe<IN>>
    Optional<T> recipeById(
            RecipeManager rm,
            RecipeType<T> type,
            ResourceLocation id
    ) {
        return rm.getAllRecipesFor(type)
                 .stream()
                 .filter(h -> id.equals(h.id()))
                 .map(net.minecraft.world.item.crafting.RecipeHolder::value)
                 .findFirst();
    }

    /**
     * Look up the recipe that matches the {@code ingredients} container and assemble its result;
     * {@link ItemStack#EMPTY} when there is no match.
     */
    public static
    <IN extends net.minecraft.world.Container,
     T extends net.minecraft.world.item.crafting.Recipe<IN>>
    ItemStack assembleMatching(
            RecipeManager rm,
            RecipeType<T> type,
            IN ingredients,
            Level level
    ) {
        return rm.getRecipeFor(type, ingredients, level)
                 .map(h -> h.value().assemble(ingredients, level.registryAccess()))
                 .orElse(ItemStack.EMPTY);
    }

    /** The (id, value) pair whose holder-id matches, or empty. */
    public static
    <IN extends net.minecraft.world.Container,
     T extends net.minecraft.world.item.crafting.Recipe<IN>>
    RecipePair<T> findById(
            RecipeManager rm,
            RecipeType<T> type,
            ResourceLocation id
    ) {
        return rm.getAllRecipesFor(type)
                 .stream()
                 .filter(h -> id.equals(h.id()))
                 .findFirst()
                 .map(RecipePair::of)
                 .orElse(null);
    }

    /** Every (id, value) pair of the given type. */
    public static
    <IN extends net.minecraft.world.Container,
     T extends net.minecraft.world.item.crafting.Recipe<IN>>
    List<RecipePair<T>> pairsOf(
            RecipeManager rm,
            RecipeType<T> type
    ) {
        return rm.getAllRecipesFor(type)
                 .stream()
                 .map(RecipePair::of)
                 .collect(Collectors.toList());
    }

    /** Every recipe id of the given type. */
    public static
    <IN extends net.minecraft.world.Container,
     T extends net.minecraft.world.item.crafting.Recipe<IN>>
    java.util.List<ResourceLocation> idsOf(
            RecipeManager rm,
            RecipeType<T> type
    ) {
        return rm.getAllRecipesFor(type)
                 .stream()
                 .map(net.minecraft.world.item.crafting.RecipeHolder::id)
                 .collect(Collectors.toList());
    }

    /** A recipe and its id. */
    public static final class RecipePair<T> {

        private final ResourceLocation id;
        private final T value;

        private RecipePair(ResourceLocation id, T value) {
            this.id = id;
            this.value = value;
        }

        private static <T extends net.minecraft.world.item.crafting.Recipe<?>> RecipePair<T> of(
                net.minecraft.world.item.crafting.RecipeHolder<T> h) {
            return new RecipePair<>(h.id(), h.value());
        }

        public ResourceLocation id() {
            return id;
        }

        public T value() {
            return value;
        }
    }
}

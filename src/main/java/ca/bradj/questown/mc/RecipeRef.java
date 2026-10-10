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
 * The 1.20 recipe-holder seam.
 *
 * <p>1.20.x split recipe data into a pair: the holder owns the recipe <b>id</b>; the value owns
 * the data plus {@code assemble(...)}/{@code getResultItem(...)}. In 1.19.2 the recipe itself
 * carried its id. A leaf never names the holder type - it calls one of these helpers:
 *
 * <ul>
 *   <li>{@link #recipesOf} - every recipe value of a type (the 1.19.2 {@code List<Recipe>} shape);</li>
 *   <li>{@link #recipeById} - the value whose holder-id matches (the value's own id can be null
 *       when codec-constructed, hence the holder lookup);</li>
 *   <li>{@link #assembleMatching} - "look up the matching recipe and assemble its result",
 *       the one-call form of the 1.19.2 {@code getRecipeFor(...).assemble(...)};</li>
 *   <li>{@link #pairsOf} - every (id, value) pair of a type, for a leaf that needs both the
 *       canonical holder-id and the recipe data.</li>
 * </ul>
 *
 * <p>The two-type-variable bounds ({@code IN extends Container, T extends Recipe<IN>}) mirror
 * the vanilla {@code RecipeManager} generic shape so its call resolves by inference.
 */
public final class RecipeRef {

    private RecipeRef() {}

    /** Every recipe value of the given type, as a plain list (the 1.19.2 shape). */
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

    /** Every (id, value) pair of the given type (the holder owns the id in 1.20.x). */
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

    /** Every recipe id of the given type (the holder owns the id in 1.20.x). */
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

    /** A neutral (id, value) recipe pair - the leaf-facing shape of the 1.20 split. */
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

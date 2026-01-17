package ca.bradj.questown.jobs;

import ca.bradj.questown.QT;
import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.integration.minecraft.MCTownItem;
import ca.bradj.questown.integration.minecraft.MCTownState;
import ca.bradj.questown.jobs.leaver.ContainerTarget;
import ca.bradj.questown.town.TownState;
import com.google.common.collect.ImmutableList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Eagerly resolves cooking during time warp.
 * <p>
 * Instead of simulating furnace ticking step-by-step, this resolver:
 * 1. Scans containers for cookable items (items with smelting recipes)
 * 2. Looks up what they cook into
 * 3. Calculates how many cook cycles fit in the available warp time
 * 4. Transforms items directly: removes raw items, adds cooked items
 * <p>
 * This approach is simpler and more reliable than virtual furnace simulation
 * because it doesn't require tracking fake block positions or syncing state.
 */
public class EagerCookResolver {

    // Default ticks per cook cycle if we can't determine from job
    // This includes: collect supplies + insert + furnace cook time + extract + drop
    // Furnace cook time is 200 ticks, plus job overhead
    public static final int DEFAULT_TICKS_PER_CYCLE = 400;

    /**
     * Resolves cooking by transforming cookable items in containers.
     * Minecraft-specific entry point.
     *
     * @param state          Current town state
     * @param level          Server level for recipe lookup
     * @param ticksPerCycle  Ticks required for one complete cook cycle (job duration)
     * @param availableTicks Total ticks available for cooking during warp
     * @return Updated state with cooking resolved
     */
    public static MCTownState resolveCooking(
            MCTownState state,
            ServerLevel level,
            long ticksPerCycle,
            long availableTicks
    ) {
        return resolveCookingGeneric(
                state,
                ticksPerCycle,
                availableTicks,
                item -> {
                    ItemStack raw = item.toQTItemStack();
                    ItemStack cooked = lookupSmeltingResult(level, raw);
                    if (cooked.isEmpty()) {
                        return null;
                    }
                    return MCTownItem.fromMCItemStack(cooked);
                },
                (rawItem, cookedItem) -> ItemStack.isSameItemSameTags(rawItem.toQTItemStack(), cookedItem.toQTItemStack()),
                MCHeldItem::fromTown
        );
    }

    /**
     * Generic resolution logic that can be unit tested without Minecraft dependencies.
     *
     * @param state          Current town state
     * @param ticksPerCycle  Ticks required for one complete cook cycle
     * @param availableTicks Total ticks available for cooking
     * @param recipeLookup   Function to look up cooked result for a raw item (returns null if not cookable)
     * @param itemMatcher    Function to check if two items match
     * @param toHeldItem     Function to convert town item to held item for depositing
     * @return Updated state with cooking resolved
     */
    public static <
            C extends ContainerTarget.Container<I>,
            I extends Item<I>,
            H extends HeldItem<H, I>,
            TOWN extends TownState<C, I, H, ?, TOWN>
            > TOWN resolveCookingGeneric(
            TOWN state,
            long ticksPerCycle,
            long availableTicks,
            Function<I, @Nullable I> recipeLookup,
            ItemMatcher<I> itemMatcher,
            Function<I, H> toHeldItem
    ) {
        if (ticksPerCycle <= 0) {
            ticksPerCycle = DEFAULT_TICKS_PER_CYCLE;
        }

        int maxCycles = (int) (availableTicks / ticksPerCycle);
        if (maxCycles <= 0) {
            return state;
        }

        TOWN result = state;
        int cyclesCompleted = 0;

        while (cyclesCompleted < maxCycles) {
            // Find next cookable item in containers
            CookableItem<I> cookable = findCookableItemGeneric(result, recipeLookup);
            if (cookable == null) {
                break;
            }

            // Remove raw item from container
            Map.Entry<TOWN, I> removeResult = result.withContainerItemRemoved(
                    item -> itemMatcher.matches(item, cookable.rawItem)
            );

            if (removeResult == null || removeResult.getValue() == null) {
                break;
            }

            result = removeResult.getKey();

            // Deposit cooked result to containers
            H cookedHeld = toHeldItem.apply(cookable.cookedResult);
            ImmutableList<H> undeposited = result.depositItems(ImmutableList.of(cookedHeld));

            if (!undeposited.isEmpty()) {
                // Put the raw item back since we couldn't complete the cycle
                result.depositItems(ImmutableList.of(toHeldItem.apply(cookable.rawItem)));
                break;
            }

            cyclesCompleted++;
        }

        QT.JOB_LOGGER.info("[EagerCook] Completed {} cooking cycles during warp", cyclesCompleted);
        return result;
    }

    /**
     * Finds the first cookable item in containers.
     */
    private static <
            C extends ContainerTarget.Container<I>,
            I extends Item<I>,
            H extends HeldItem<H, I>,
            TOWN extends TownState<C, I, H, ?, TOWN>
            > CookableItem<I> findCookableItemGeneric(
            TOWN state,
            Function<I, @Nullable I> recipeLookup
    ) {
        for (var container : state.containers) {
            for (int i = 0; i < container.size(); i++) {
                I item = container.getItem(i);
                if (item.isEmpty()) {
                    continue;
                }

                I cooked = recipeLookup.apply(item);
                if (cooked != null) {
                    return new CookableItem<>(item, cooked);
                }
            }
        }
        return null;
    }

    /**
     * Looks up the smelting recipe result for an input item.
     */
    private static ItemStack lookupSmeltingResult(ServerLevel level, ItemStack input) {
        if (input.isEmpty()) {
            return ItemStack.EMPTY;
        }

        SimpleContainer container = new SimpleContainer(input.copy());

        Optional<SmeltingRecipe> recipe = level.getRecipeManager()
                .getRecipeFor(RecipeType.SMELTING, container, level);

        return recipe.map(r -> r.getResultItem().copy()).orElse(ItemStack.EMPTY);
    }

    /**
     * Functional interface for matching items.
     */
    @FunctionalInterface
    public interface ItemMatcher<I> {
        boolean matches(I a, I b);
    }

    /**
     * Record holding a cookable item and its cooked result.
     */
    private record CookableItem<I>(I rawItem, I cookedResult) {
    }
}

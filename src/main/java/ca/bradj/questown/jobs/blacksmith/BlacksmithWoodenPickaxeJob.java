package ca.bradj.questown.jobs.blacksmith;

import ca.bradj.questown.Questown;
import ca.bradj.questown.blocks.BlacksmithsTableBlock;
import ca.bradj.questown.integration.minecraft.MCTownItem;
import ca.bradj.questown.jobs.*;
import ca.bradj.questown.jobs.declarative.SoundInfo;
import ca.bradj.questown.mc.Util;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

import static ca.bradj.questown.jobs.WorksBehaviour.productionWork;
import static ca.bradj.questown.jobs.blacksmith.nomc.BlacksmithWoodenPickaxeWork.DEFINITION;

public class BlacksmithWoodenPickaxeJob {
    public static JobDefinition DEF = DEFINITION;

    public static final int PAUSE_FOR_ACTION = 100;

    public static Work asWork() {
        return productionWork(
                Items.WOODEN_PICKAXE.getDefaultInstance(),
                DEFINITION.jobId(),
                null,
                new WorkDescription(
                        t -> ImmutableSet.of(toItem(DEFINITION.result())),
                        toItemStack(DEFINITION.result())
                ),
                new WorkLocation(
                        (block) -> block.getBlock() instanceof BlacksmithsTableBlock,
                        Questown.ResourceLocation("smithy")
                ),
                new WorkStates(
                        DEFINITION.maxState(),
                        toIngredient(DEFINITION.ingredientsRequiredAtStates()),
                        Util.constant(DEFINITION.ingredientQtyRequiredAtStates()),
                        toIngredient(DEFINITION.toolsRequiredAtStates()),
                        Util.constant(DEFINITION.workRequiredAtStates()),
                        Util.constant(DEFINITION.timeRequiredAtStates())
                ),
                new WorkWorldInteractions(
                        PAUSE_FOR_ACTION,
                        WorksBehaviour.singleItemOutput(() -> toItemStack(DEFINITION.result()).copy())
                ),
                WorksBehaviour.standardProductionRules(),
                SoundInfo.everyInterval(SoundEvents.WOOD_HIT.getLocation())
        );
    }

    private static ItemStack toItemStack(String result) {
        Item knownItem = Objects.requireNonNull(KNOWN_ITEMS.get(result));
        return knownItem.getDefaultInstance();
    }

    private static MCTownItem toItem(String result) {
        return MCTownItem.fromMCItemStack(toItemStack(result));
    }

    private static final ImmutableMap<String, Item> KNOWN_ITEMS = ImmutableMap.of(
            "minecraft:wooden_pickaxe", Items.WOODEN_PICKAXE
    );
    private static final ImmutableMap<String, Supplier<Ingredient>> KNOWN_INGREDIENTS = ImmutableMap.of(
            "minecraft:stick", () -> Ingredient.of(Items.STICK),
            "#minecraft:planks", () -> Ingredient.of(ItemTags.PLANKS)
    );

    private static ImmutableMap<Integer, Supplier<Ingredient>> toIngredient(Map<Integer, String> map) {
        ImmutableMap.Builder<Integer, Supplier<Ingredient>> b = ImmutableMap.builder();
        map.forEach((k, v) -> b.put(k, Objects.requireNonNull(KNOWN_INGREDIENTS.get(v))));
        return b.build();
    }
}

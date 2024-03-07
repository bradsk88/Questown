package ca.bradj.questown.jobs.gatherer;

import ca.bradj.questown.blocks.WelcomeMatBlock;
import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.jobs.*;
import ca.bradj.questown.jobs.production.ProductionStatus;
import ca.bradj.questown.town.special.SpecialQuests;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.BiFunction;

import static ca.bradj.questown.jobs.WorksBehaviour.productionWork;

public class NewLeaverWork {

    public static List<GathererTools.LootTableParameters> getAllParameters() {
        return allParameters;
    }

    protected static final List<GathererTools.LootTableParameters> allParameters = new ArrayList<>();

    public NewLeaverWork(
            GathererTools.LootTableParameters lootTableParams
    ) {
        if (!allParameters.contains(lootTableParams)) {
            throw new IllegalStateException("Descendants of NewLeaveWork must register selves from a static context");
        }
    }

    protected static ImmutableList<String> standardRules() {
        return ImmutableList.of(
                SpecialRules.PRIORITIZE_EXTRACTION,
                SpecialRules.NULLIFY_EXCESS_RESULTS
                // Gatherers cannot "carry more results home" than their inventory can hold
        );
    }

    protected static Work asWork(
            JobID id,
            ItemStack icon,
            GathererTools.LootTablePrefix lootTablePrefix,
            ItemStack initialRequest,
            int maxState,
            ImmutableMap<Integer, Ingredient> ingredientsRequiredAtStates,
            ImmutableMap<Integer, Integer> ingredientQtyRequiredAtStates,
            ImmutableMap<Integer, Ingredient> toolsRequiredAtStates,
            ImmutableMap<Integer, Integer> workRequiredAtStates,
            ImmutableMap<Integer, Integer> timeRequiredAtStates,
            ImmutableMap<ProductionStatus, String> specialRules,
            BiFunction<ServerLevel, Collection<MCHeldItem>, Iterable<MCHeldItem>> resultGenerator
    ) {
        return productionWork(
                icon,
                id,
                new WorkDescription(
                        t -> t.allKnownGatherItemsFn().apply(lootTablePrefix),
                        initialRequest
                ),
                new WorkLocation(
                        block -> block instanceof WelcomeMatBlock,
                        SpecialQuests.TOWN_GATE
                ),
                new WorkStates(
                        maxState,
                        ingredientsRequiredAtStates,
                        ingredientQtyRequiredAtStates,
                        toolsRequiredAtStates,
                        workRequiredAtStates,
                        timeRequiredAtStates
                ),
                new WorkWorldInteractions(
                        0,
                        resultGenerator
                ),
                new WorkSpecialRules(
                        specialRules,
                        standardRules()
                ),
                SoundEvents.ARMOR_EQUIP_LEATHER.getLocation()
        );
    }
}

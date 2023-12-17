package ca.bradj.questown.jobs.gatherer;

import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.integration.minecraft.MCTownItem;
import ca.bradj.questown.jobs.DeclarativeJob;
import ca.bradj.questown.jobs.JobID;
import ca.bradj.questown.jobs.declarative.ProductionJournal;
import ca.bradj.questown.jobs.production.ProductionStatus;
import com.google.common.collect.ImmutableMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.crafting.Ingredient;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BiFunction;

public class NewLeaverWork extends DeclarativeJob {

    public static List<GathererTools.LootTableParameters> getAllParameters() {
        return allParameters;
    }

    private static final List<GathererTools.LootTableParameters> allParameters = new ArrayList<>();

    public NewLeaverWork(
            UUID ownerUUID,
            GathererTools.LootTablePrefix lootTablePrefix,
            GathererTools.LootTablePath defaultLootTablePath,
            int inventoryCapacity,
            @NotNull JobID jobId,
            ResourceLocation workRoomId,
            int maxState,
            boolean prioritizeExtraction,
            int workInterval,
            ImmutableMap<Integer, Ingredient> ingredientsRequiredAtStates,
            ImmutableMap<Integer, Integer> ingredientsQtyRequiredAtStates,
            ImmutableMap<Integer, Ingredient> toolsRequiredAtStates,
            ImmutableMap<Integer, Integer> workRequiredAtStates,
            ImmutableMap<Integer, Integer> timeRequiredAtStates,
            boolean sharedTimers,
            ImmutableMap<ProductionStatus, String> specialRules,
            BiFunction<ServerLevel, ProductionJournal<MCTownItem, MCHeldItem>, Iterable<MCHeldItem>> workResult
    ) {
        super(
                ownerUUID,
                inventoryCapacity,
                jobId,
                workRoomId,
                maxState,
                prioritizeExtraction,
                workInterval,
                ingredientsRequiredAtStates,
                ingredientsQtyRequiredAtStates,
                toolsRequiredAtStates,
                workRequiredAtStates,
                timeRequiredAtStates,
                sharedTimers,
                specialRules,
                workResult
        );
        allParameters.add(new GathererTools.LootTableParameters(
                lootTablePrefix, defaultLootTablePath
        ));
    }
}

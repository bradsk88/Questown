package ca.bradj.questown.gui;

import ca.bradj.questown.Questown;
import ca.bradj.questown.jobs.GathererJournal;
import ca.bradj.questown.jobs.IStatus;
import ca.bradj.questown.jobs.production.ProductionStatus;
import com.google.common.collect.ImmutableMap;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;

public class StatusArt {

    private static final Map<ProductionStatus, String> pArt;

    static {
        ImmutableMap.Builder<ProductionStatus, String> b = ImmutableMap.builder();
        b.put(ProductionStatus.IDLE, "menu/gatherer/idle.png");
        b.put(ProductionStatus.NO_SPACE, "menu/gatherer/no_space.png");
        b.put(ProductionStatus.RELAXING, "menu/gatherer/relaxing.png");
        b.put(ProductionStatus.NO_JOBSITE, "menu/gatherer/no_jobsite.png");
        b.put(ProductionStatus.GOING_TO_JOB, "menu/gatherer/leaving.png");
        pArt = b.build();
    }

    public static ResourceLocation getTexture(IStatus<?> status) {
        if (status instanceof GathererJournal.Status gjc) {
            return switch (gjc) {
                case UNSET -> new ResourceLocation("questown", "textures/error.png");
                case IDLE -> new ResourceLocation("questown", "textures/menu/gatherer/idle.png");
                case NO_SPACE -> new ResourceLocation("questown", "textures/menu/gatherer/no_space.png");
                case NO_FOOD -> new ResourceLocation("questown", "textures/menu/gatherer/no_food.png");
                case NO_GATE -> new ResourceLocation("questown", "textures/menu/gatherer/no_gate.png");
                case STAYING ->
                    // TODO: Icon for this
                        new ResourceLocation("questown", "textures/menu/gatherer/idle.png");
                case GATHERING -> new ResourceLocation("questown", "textures/menu/gatherer/leaving.png");
                case RETURNED_SUCCESS ->
                        new ResourceLocation("questown", "textures/menu/gatherer/returned_success.png");
                case RETURNED_FAILURE ->
                    // TODO: Icon for this
                        new ResourceLocation("questown", "textures/error.png");
                case RETURNING ->
                    // TODO: Icon for this
                        new ResourceLocation("questown", "textures/error.png");
                case CAPTURED ->
                    // TODO: Icon for this
                        new ResourceLocation("questown", "textures/error.png");
                case RELAXING ->
                    // TODO: Icon for this
                        new ResourceLocation("questown", "textures/menu/gatherer/relaxing.png");
                case DROPPING_LOOT, GATHERING_EATING, GATHERING_HUNGRY, RETURNING_AT_NIGHT ->
                    // TODO: Icon for this
                        new ResourceLocation("questown", "textures/error.png");
                case GOING_TO_JOBSITE, NO_JOBSITE, FARMING_HARVESTING, FARMING_RANDOM_TEND, LEAVING_FARM,
                        FARMING_PLANTING, FARMING_TILLING, FARMING_BONING,
                        FARMING_COMPOSTING, FARMING_WEEDING,
                        COLLECTING_SUPPLIES,
                        NO_SUPPLIES, BAKING, BAKING_FUELING, COLLECTING_BREAD ->
                    // TODO: Icon for this
                        new ResourceLocation("questown", "textures/error.png");
            };
        }
        if (status instanceof ProductionStatus ps) {
            String art = pArt.get(ps);
            if (art != null) {
                return Questown.ResourceLocation( "textures/" + art);
            }
        }

        // TODO: Smelter statuses
        return new ResourceLocation("questown", "textures/error.png");
    }
}

package ca.bradj.questown.gui;

import ca.bradj.questown.jobs.GathererJournal;
import ca.bradj.questown.jobs.IStatus;
import net.minecraft.resources.ResourceLocation;

public class StatusArt {
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
                case RETURNED_SUCCESS -> new ResourceLocation("questown", "textures/menu/gatherer/returned_success.png");
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
                case GOING_TO_JOBSITE, FARMING_HARVESTING, FARMING_RANDOM_TEND, LEAVING_FARM,
                        FARMING_PLANTING, FARMING_TILLING, FARMING_BONING,
                        FARMING_COMPOSTING, FARMING_WEEDING,
                        COLLECTING_SUPPLIES,
                        NO_SUPPLIES, BAKING, BAKING_FUELING, COLLECTING_BREAD ->
                    // TODO: Icon for this
                        new ResourceLocation("questown", "textures/error.png");  
            };
        }

        // TODO: Smelter statuses
        return new ResourceLocation("questown", "textures/error.png");
    }
}

package ca.bradj.questown.town;

import ca.bradj.questown.Questown;
import net.minecraft.resources.ResourceLocation;

/** Mood effects villagers get, for example after eating. */
public final class MoodEffects {

    private MoodEffects() {
    }

    public static final ResourceLocation UNCOMFORTABLE_EATING = Questown.ResourceLocation("uncomfortable_eating");
    public static final ResourceLocation COMFORTABLE_EATING = Questown.ResourceLocation("comfortable_eating");
    public static final ResourceLocation ATE_RAW_FOOD = Questown.ResourceLocation("are_raw_food");
    /** At dawn, for a villager that had no bed last night: it works more slowly that day. */
    public static final ResourceLocation SLEEPLESS = Questown.ResourceLocation("sleepless");
}

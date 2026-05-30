package ca.bradj.questown.town;

import ca.bradj.questown.Questown;
import net.minecraft.resources.ResourceLocation;

/**
 * Mood effect identities applied to villagers (e.g. after eating). Applied via
 * the special-rules system ({@code ApplyMoodEffectSpecialRule}), not as item
 * results — see ADR-0003.
 */
public final class MoodEffects {

    private MoodEffects() {
    }

    public static final ResourceLocation UNCOMFORTABLE_EATING = Questown.ResourceLocation("uncomfortable_eating");
    public static final ResourceLocation COMFORTABLE_EATING = Questown.ResourceLocation("comfortable_eating");
    public static final ResourceLocation ATE_RAW_FOOD = Questown.ResourceLocation("are_raw_food");
}

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
    /** Applied at dawn to a townie that had no bed for the previous night: it works
     *  more slowly for the day (mood input per the 2026-07-27 town-quality decision). */
    public static final ResourceLocation SLEEPLESS = Questown.ResourceLocation("sleepless");
}

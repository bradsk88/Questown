package ca.bradj.questown.jobs;

import ca.bradj.questown.items.EffectMetaItem;
import ca.bradj.questown.town.Effect;
import net.minecraft.resources.ResourceLocation;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class WorkEffects {

    private static Map<ResourceLocation, Float> time_factors = new HashMap<>();

    static {
        time_factors.put(EffectMetaItem.MoodEffects.UNCOMFORTABLE_EATING, 1.1f);
        time_factors.put(EffectMetaItem.MoodEffects.COMFORTABLE_EATING, 0.9f);
    }

    public static float calculateTimeFactor(Collection<Effect> effects) {
        return effects
                .stream()
                .map(v -> time_factors.getOrDefault(v.effect(), 1.0f))
                .reduce(1.0f, (cur, next) -> cur * next);
    }
}

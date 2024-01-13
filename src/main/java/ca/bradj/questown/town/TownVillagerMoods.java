package ca.bradj.questown.town;

import ca.bradj.questown.QT;
import ca.bradj.questown.core.Config;
import ca.bradj.questown.items.EffectMetaItem;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class TownVillagerMoods {

    // Static
    static Map<ResourceLocation, Integer> buffs;
    private static boolean staticInitialized = false;

    static void staticInitialize() {
        ImmutableMap.Builder<ResourceLocation, Integer> b = ImmutableMap.builder();

        b.put(EffectMetaItem.MoodEffects.COMFORTABLE_EATING, 5);
        b.put(EffectMetaItem.MoodEffects.UNCOMFORTABLE_EATING, -5);

        buffs = b.build();
        staticInitialized = true;
    }

    // Persisted
    private final Map<UUID, ImmutableList<Effect>> moodEffects = new HashMap<>();

    // Computed at runtime
    private final Map<UUID, Integer> mood = new HashMap<>();
    private long moodTick = Config.MOOD_TICK_INTERVAL.get();

    public static int compute(Collection<Effect> effects) {
        Integer neutral = Config.NEUTRAL_MOOD.get();
        Integer computed = effects.stream()
                .map(Effect::effect)
                .map(buffs::get)
                .reduce(Integer::sum)
                .orElse(neutral);
        return Math.max(Math.min(neutral + computed, 100), 0);
    }

    public void tick(long currentTick) {
        if (!staticInitialized) {
            throw new IllegalStateException("Moods registry not initialized");
        }
        moodTick--;
        if (moodTick > 0) {
            return;
        }
        moodTick = Config.MOOD_TICK_INTERVAL.get();
        moodEffects.forEach((uuid, effects) -> mood.put(uuid, compute(effects)));
        moodEffects.keySet().forEach(
                i -> moodEffects.computeIfPresent(i, (uuid, effects) -> removeExpired(currentTick, effects))
        );
    }

    @NotNull
    private static ImmutableList<Effect> removeExpired(long currentTick, ImmutableList<Effect> effects) {
        ImmutableList.Builder<Effect> b = ImmutableList.builder();
        effects.stream().filter(v -> v.untilTick() >= currentTick).forEach(b::add);
        return b.build();
    }

    public float getMood(UUID uuid) {
        return mood.getOrDefault(uuid, Config.NEUTRAL_MOOD.get()) / 100f;
    }

    public void tryApplyEffect(ResourceLocation effect, Long expireOnTick, UUID uuid) {
        ImmutableList<Effect> current = moodEffects.getOrDefault(uuid, ImmutableList.of());
        ImmutableList.Builder<Effect> b = ImmutableList.builder();
        b.addAll(current);
        if (buffs.containsKey(effect)) {
            Effect e = new Effect(effect, expireOnTick);
            b.add(e);
            QT.VILLAGER_LOGGER.debug("Effect has been applied to {}: {}", uuid, e);
        }
        moodEffects.put(uuid, b.build());
    }

    public void initialize(Map<UUID, ImmutableList<Effect>> moodEffects) {
        if (!this.moodEffects.isEmpty()) {
            throw new IllegalStateException("Attempting to initialize already active mood effects");
        }
        this.moodEffects.putAll(moodEffects);
    }

    public ImmutableMap<UUID, ImmutableList<Effect>> getEffects() {
        return ImmutableMap.copyOf(moodEffects);
    }
}

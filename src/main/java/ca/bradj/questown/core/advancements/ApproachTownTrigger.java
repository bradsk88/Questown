package ca.bradj.questown.core.advancements;

import ca.bradj.questown.Questown;
import com.google.common.collect.BiMap;
import com.google.common.collect.ImmutableBiMap;
import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.EntityPredicate;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;

public class ApproachTownTrigger extends SimpleCriterionTrigger<ApproachTownTrigger.Instance> {

    public static final ResourceLocation ID = new ResourceLocation(
            Questown.MODID, "approach_town"
    );

    public enum Triggers {
        Invalid,
        FirstVisit;

        private static final BiMap<Triggers, String> stringVals = ImmutableBiMap.of(
                Triggers.FirstVisit, "first_visit"
        );

                public static final Codec<Triggers> CODEC = Codec.STRING.xmap(
                key -> {
                    Triggers t = stringVals.inverse().get(key);
                    if (t == null) {
                        throw new IllegalArgumentException(String.format("Trigger string is unexpected: %s", key));
                    }
                    return t;
                },
                Triggers::getID
        );

        public static Triggers fromJSON(JsonElement trick_id) {
            String key = trick_id.getAsString();
            if (!stringVals.inverse().containsKey(key)) {
                throw new IllegalArgumentException(
                        String.format("Approach trigger ID is unexpected: %s", trick_id)
                );
            }
            return stringVals.inverse().get(key);
        }

        public String getID() {
            return stringVals.get(this);
        }
    }

    @Override
    public Codec<Instance> codec() {
        return Instance.CODEC;
    }

    public void trigger(
            ServerPlayer player,
            Triggers trickID
    ) {
        // A non-player command source (server console, command block, /execute … as)
        // passes a null player. Vanilla SimpleCriterionTrigger.trigger would NPE on
        // player.getAdvancements() and crash the server, so skip the null player.
        if (player == null) {
            return;
        }
        super.trigger(player, instance -> instance.matches(trickID));
    }

    public record Instance(Optional<ContextAwarePredicate> player, Triggers trickID) implements SimpleCriterionTrigger.SimpleInstance {
        public Instance {
            if (Triggers.Invalid.equals(trickID)) {
                throw new IllegalArgumentException("context must not be invalid");
            }
        }

        public boolean matches(Triggers triggerID) {
            return this.trickID.equals(triggerID);
        }

        public static final Codec<Instance> CODEC = RecordCodecBuilder.create(
                instance -> instance.group(
                        EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(Instance::player),
                        Triggers.CODEC.fieldOf("context").forGetter(Instance::trickID)
                ).apply(instance, Instance::new)
        );
    }
}
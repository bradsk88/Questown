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
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.Optional;

public class TutorialTrigger extends SimpleCriterionTrigger<TutorialTrigger.Instance> {

    public static final ResourceLocation ID = new ResourceLocation(
            Questown.MODID, "tutorial_trigger"
    );

    public void triggerForNearestPlayer(
            ServerLevel level,
            Triggers trigger,
            BlockPos nearPos
    ) {
        Player np = level.getNearestPlayer(
                nearPos.getX(), nearPos.getY(), nearPos.getZ(), 16.0D, false
        );
        if (!(np instanceof ServerPlayer sp)) {
            return;
        }
        trigger(sp, trigger);
    }

    public enum Triggers {
        Invalid,
        TutorialComplete,
        SecondJobType,
        FirstWarp,
        FirstRoomUpgrade,
        FirstBopView,
        FirstBopSpend,
        Chapter2,
        Chapter3,
        Chapter4,
        FirstCampfireSleep,
        // Curated path finished (agent playtest 2026-10-27 #4): "path_" + the path id's path.
        OutfitterPath;

        private static final BiMap<Triggers, String> stringVals = ImmutableBiMap.<Triggers, String>builder()
                .put(Triggers.TutorialComplete, "tutorial_complete")
                .put(Triggers.SecondJobType, "second_job_type")
                .put(Triggers.FirstWarp, "first_warp")
                .put(Triggers.FirstRoomUpgrade, "first_room_upgrade")
                .put(Triggers.FirstBopView, "first_bop_view")
                .put(Triggers.FirstBopSpend, "first_bop_spend")
                .put(Triggers.Chapter2, "chapter_2")
                .put(Triggers.Chapter3, "chapter_3")
                .put(Triggers.Chapter4, "chapter_4")
                .put(Triggers.FirstCampfireSleep, "first_campfire_sleep")
                .put(Triggers.OutfitterPath, "path_outfitter")
                .build();

        /** The trigger for finishing curated path {@code pathId} ("questown:outfitter"), if it has one. */
        public static Optional<Triggers> forPath(String pathId) {
            String name = pathId.substring(pathId.indexOf(':') + 1);
            return Optional.ofNullable(stringVals.inverse().get("path_" + name));
        }

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

        public static Triggers fromJSON(JsonElement element) {
            String key = element.getAsString();
            if (!stringVals.inverse().containsKey(key)) {
                throw new IllegalArgumentException(
                        String.format("Tutorial trigger ID is unexpected: %s", element)
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
            Triggers trigger
    ) {
        super.trigger(player, instance -> instance.matches(trigger));
    }

    public record Instance(Optional<ContextAwarePredicate> player, Triggers context) implements SimpleCriterionTrigger.SimpleInstance {
        public Instance {
            if (Triggers.Invalid.equals(context)) {
                throw new IllegalArgumentException("context must not be invalid");
            }
        }

        public boolean matches(Triggers triggerID) {
            return this.context.equals(triggerID);
        }

        public static final Codec<Instance> CODEC = RecordCodecBuilder.create(
                instance -> instance.group(
                        EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(Instance::player),
                        Triggers.CODEC.fieldOf("context").forGetter(Instance::context)
                ).apply(instance, Instance::new)
        );
    }
}
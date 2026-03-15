package ca.bradj.questown.core.advancements;

import ca.bradj.questown.Questown;
import com.google.common.collect.BiMap;
import com.google.common.collect.ImmutableBiMap;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.advancements.critereon.AbstractCriterionTriggerInstance;
import net.minecraft.advancements.critereon.DeserializationContext;
import net.minecraft.advancements.critereon.EntityPredicate;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.function.Predicate;

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
        Chapter4;

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
                .build();

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
    public ResourceLocation getId() {
        return ID;
    }

    @Override
    public Instance createInstance(
            JsonObject json,
            EntityPredicate.Composite predicate,
            DeserializationContext parser
    ) {
        if (!json.has("context")) {
            throw new IllegalStateException(String.format(
                    "Trigger of type %s is missing context [ID: %s]",
                    ID, parser.getAdvancementId()
            ));
        }

        return new TutorialTrigger.Instance(predicate, Triggers.fromJSON(json.get("context")));
    }

    @Override
    protected void trigger(
            ServerPlayer player,
            Predicate<Instance> predicate
    ) {
        super.trigger(player, predicate);
    }

    public void trigger(
            ServerPlayer player,
            Triggers trigger
    ) {
        super.trigger(player, instance -> instance.matches(trigger));
    }

    public static class Instance extends AbstractCriterionTriggerInstance {
        private final Triggers context;

        public Instance(
                EntityPredicate.Composite predicate,
                Triggers context
        ) {
            super(TutorialTrigger.ID, predicate);
            if (Triggers.Invalid.equals(context)) {
                throw new IllegalArgumentException("context must not be invalid");
            }
            this.context = context;
        }

        public boolean matches(Triggers trigger) {
            return this.context.equals(trigger);
        }
    }
}

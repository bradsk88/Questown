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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.function.Predicate;

public class VisitorTrigger extends SimpleCriterionTrigger<VisitorTrigger.Instance> {

    public static final ResourceLocation ID = new ResourceLocation(
            Questown.MODID, "visitor_trigger"
    );

    public enum Triggers {
        Invalid,
        FirstVisitor;

        private static final BiMap<Triggers, String> stringVals = ImmutableBiMap.of(
                Triggers.FirstVisitor, "first_visitor"
        );

        public static Triggers fromJSON(JsonElement trick_id) {
            String key = trick_id.getAsString();
            if (!stringVals.inverse().containsKey(key)) {
                throw new IllegalArgumentException(
                        String.format("Visitor trigger ID is unexpected: %s", trick_id)
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
        if (!json.has("visitor_id")) {
            throw new IllegalStateException(String.format(
                    "Trigger of type %s is missing visitor_id [ID: %s]",
                    ID, parser.getAdvancementId()
            ));
        }

        return new VisitorTrigger.Instance(predicate, Triggers.fromJSON(json.get("visitor_id")));
    }

    @Override
    protected void trigger(
            ServerPlayer p_235959_1_,
            Predicate<Instance> p_235959_2_
    ) {
        super.trigger(p_235959_1_, p_235959_2_);
    }

    public void trigger(
            ServerPlayer player,
            Triggers trickID
    ) {
        super.trigger(player, instance -> instance.matches(trickID));
    }

    public static class Instance extends AbstractCriterionTriggerInstance {
        private final Triggers trickID;

        public Instance(
                EntityPredicate.Composite p_i231464_2_,
                Triggers trickID
        ) {
            super(VisitorTrigger.ID, p_i231464_2_);
            if (Triggers.Invalid.equals(trickID)) {
                throw new IllegalArgumentException("visitorID must not be invalid");
            }
            this.trickID = trickID;
        }

        public boolean matches(
                Triggers trickID
        ) {
            return this.trickID.equals(trickID);
        }
    }
}

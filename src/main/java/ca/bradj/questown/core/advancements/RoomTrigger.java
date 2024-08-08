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

public class RoomTrigger extends SimpleCriterionTrigger<RoomTrigger.Instance> {

    public static final ResourceLocation ID = new ResourceLocation(
            Questown.MODID, "room_trigger"
    );

    public void triggerForNearestPlayer(
            ServerLevel level,
            Triggers triggers,
            BlockPos nearPos
    ) {
        Player np = level.getNearestPlayer(
                nearPos.getX(), nearPos.getY(), nearPos.getZ(), 8.0D, false
        );
        if (!(np instanceof ServerPlayer sp)) {
            return;
        }
        trigger(sp, triggers);
    }

    public enum Triggers {
        Invalid,
        FirstRoom,
        FirstJobBlock,
        FirstJobBoard,
        WandGet; // TODO: Put this on "ItemTrigger" (new class) instead

        private static final BiMap<Triggers, String> stringVals = ImmutableBiMap.of(
                Triggers.FirstRoom, "first_room",
                Triggers.FirstJobBlock, "first_job_block",
                Triggers.FirstJobBoard, "first_job_board",
                Triggers.WandGet, "wand_get"
        );

        public static Triggers fromJSON(JsonElement trick_id) {
            String key = trick_id.getAsString();
            if (!stringVals.inverse().containsKey(key)) {
                throw new IllegalArgumentException(
                        String.format("Room trigger ID is unexpected: %s", trick_id)
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

        return new RoomTrigger.Instance(predicate, Triggers.fromJSON(json.get("context")));
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
        private final Triggers context;

        public Instance(
                EntityPredicate.Composite p_i231464_2_,
                Triggers context
        ) {
            super(RoomTrigger.ID, p_i231464_2_);
            if (Triggers.Invalid.equals(context)) {
                throw new IllegalArgumentException("context must not be invalid");
            }
            this.context = context;
        }

        public boolean matches(
                Triggers trickID
        ) {
            return this.context.equals(trickID);
        }
    }
}

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
        FirstStoreRoom,
        FirstWelcomeMat,
        WandGet, // TODO: Put this on "ItemTrigger" (new class) instead
        FirstOpenFlagMenu;

        private static final BiMap<Triggers, String> stringVals = ImmutableBiMap.of(
                Triggers.FirstRoom, "first_room",
                Triggers.FirstJobBlock, "first_job_block",
                Triggers.FirstJobBoard, "first_job_board",
                Triggers.FirstStoreRoom, "first_store_room",
                Triggers.FirstWelcomeMat, "first_welcome_mat",
                Triggers.WandGet, "wand_get",
                Triggers.FirstOpenFlagMenu, "first_open_flag_menu"
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
    public Codec<Instance> codec() {
        return Instance.CODEC;
    }

    public void trigger(
            ServerPlayer player,
            Triggers trickID
    ) {
        super.trigger(player, instance -> instance.matches(trickID));
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
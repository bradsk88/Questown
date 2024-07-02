package ca.bradj.questown.jobs;

import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.mc.Util;
import ca.bradj.questown.town.interfaces.TownInterface;
import com.google.common.collect.ImmutableList;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.tags.TagKey;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

public class ResourceJobLoader {

    // FIXME: We probably need to register this listener somewhere
    //  https://github.com/CodeeToasty/Create/blob/42763b4480e059be5c6cd23f8cb4e9031296fc0c/src/main/java/com/simibubi/create/content/curiosities/weapons/PotatoProjectileTypeManager.java#L109

    public static class ReloadListener extends SimpleJsonResourceReloadListener {

        private static final Gson GSON = new Gson();

        public static final ReloadListener INSTANCE = new ReloadListener();

        protected ReloadListener() {
            super(GSON, "questown.jobs");
        }

        @Override
        protected void apply(Map<ResourceLocation, JsonElement> map, ResourceManager resourceManager, ProfilerFiller profiler) {
            for (Map.Entry<ResourceLocation, JsonElement> entry : map.entrySet()) {
                JsonElement element = entry.getValue();
                if (element.isJsonObject()) {
                    ResourceLocation id = entry.getKey();
                    JsonObject object = element.getAsJsonObject();
                    Work type = workFromJson(object);
                    // TOOD: Register with "Works"
                }
            }
        }

        private Work workFromJson(JsonObject object) {
            Item iconItem = ForgeRegistries.ITEMS.getValue(required(object, "icon"));
            if (iconItem == null) {
                throw new IllegalArgumentException("Icon image does not exist: " + object.get("icon").getAsString());
            }
            Item resultItem = ForgeRegistries.ITEMS.getValue(required(object, "result"));
            if (resultItem == null) {
                throw new IllegalArgumentException("Result item does not exist: " + object.get("icon").getAsString());
            }
            // TODO: Result quantity
            Predicate<Block> isJobBlock = ResourceJobLoader.isJobBlock(object.get("block").getAsString());
            return WorksBehaviour.productionWork(
                    iconItem.getDefaultInstance(),
                    JobID.fromJSON(Util.getOrDefault(object, "id", JsonElement::getAsString, null)),
                    JobID.fromJSON(Util.getOrDefault(object, "parent", JsonElement::getAsString, null)),
                    WorksBehaviour.standardDescription(resultItem::getDefaultInstance),
                    new WorkLocation(isJobBlock, required(object, "room")),
                    ResourceJobLoader.workStates(required(object, "work_states")),
                    WorksBehaviour.standardWorldInteractions(
                            requiredInt(object, "cooldown_ticks"),
                            resultItem::getDefaultInstance
                    ),
                    WorksBehaviour.standardProductionRules(),
                    optional(object, "sound")
            );
        }

        private @Nullable ResourceLocation optional(
                JsonObject object,
                String k
        ) {
            if (!object.has(k)) {
                return null;
            }
            return new ResourceLocation(object.get(k).getAsString());
        }

        private int requiredInt(
                JsonObject object,
                String k
        ) {
            if (!object.has(k)) {
                throw new IllegalArgumentException(k + " is required");
            }
            return object.get(k).getAsInt();
        }

        private ResourceLocation required(JsonObject object, String k) {
            if (!object.has(k)) {
                throw new IllegalArgumentException(k + " is required");
            }
            String iconName = object.get(k).getAsString();
            return new ResourceLocation(iconName);
        }
    }

    private static WorkStates workStates(ResourceLocation workStates) {
        // FIXME: Read array of states from JSON (infer maxstate)
        return null;
    }

    private static Predicate<Block> isJobBlock(String block) {
        if (block.startsWith("#")) {
            Ingredient ing = Ingredient.of(new TagKey<>(
                    Registry.ITEM_REGISTRY,
                    new ResourceLocation(block.split("#")[1])
            ));
            return (Block b) -> ing.test(b.asItem().getDefaultInstance());
        }
        return b -> Ingredient.of(ForgeRegistries.ITEMS.getValue(new ResourceLocation(block))).test(b.asItem().getDefaultInstance());
    }

    private static ImmutableSnapshot<MCHeldItem, ?> snapshot(
            JobID jobID,
            String s,
            ImmutableList<MCHeldItem> mcHeldItems
    ) {
        // FIXME: Implement
        return null;
    }

    private static Job<MCHeldItem, ? extends ImmutableSnapshot<MCHeldItem, ?>, ? extends IStatus<?>> jobFunc(
            TownInterface townInterface,
            UUID uuid,
            JsonObject object
    ) {
        // FIXME: Implement
        return null;
    }
}

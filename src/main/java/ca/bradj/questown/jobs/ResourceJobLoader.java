package ca.bradj.questown.jobs;

import ca.bradj.questown.QT;
import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.mc.Util;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.tags.TagKey;
import net.minecraft.util.profiling.InactiveProfiler;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import java.util.function.Supplier;

public class ResourceJobLoader {

    // FIXME: We probably need to register this listener somewhere
    //  https://github.com/CodeeToasty/Create/blob/42763b4480e059be5c6cd23f8cb4e9031296fc0c/src/main/java/com/simibubi/create/content/curiosities/weapons/PotatoProjectileTypeManager.java#L109

    public static final ReloadListener LISTENER = new ReloadListener();

    public static class ReloadListener extends SimpleJsonResourceReloadListener {

        private static final Gson GSON = new Gson();

        public ImmutableMap<JobID, Work> getJobs() {
            return jobs;
        }

        private ImmutableMap<JobID, Work> jobs;

        protected ReloadListener() {
            super(GSON, "questown_jobs");
        }

        @Override
        protected void apply(
                Map<ResourceLocation, JsonElement> map,
                ResourceManager resourceManager,
                ProfilerFiller profiler
        ) {
            ImmutableMap.Builder<JobID, Work> b = ImmutableMap.builder();
            for (Map.Entry<ResourceLocation, JsonElement> entry : map.entrySet()) {
                JsonElement element = entry.getValue();
                if (element.isJsonObject()) {
                    ResourceLocation id = entry.getKey();
                    JsonObject object = element.getAsJsonObject();
                    try {
                        int version = requiredInt(object, "version");
                        Work type = switch (version) {
                            case 1 -> workFromJsonV1(object);
                            default -> throw new IllegalArgumentException(String.format(
                                    "Unknown job file version \"%s\"", version
                            ));
                        };
                        QT.INIT_LOGGER.debug("Work found in filesystem: {}", type);
                        b.put(type.id(), type);
                    } catch (Exception e) {
                        QT.INIT_LOGGER.error("Failed to load work {}", id, e);
                    }
                }
            }
            this.jobs = b.build();
        }

        public void loadFromFiles(ResourceManager man) {
            Map<ResourceLocation, JsonElement> map = prepare(man, InactiveProfiler.INSTANCE);
            apply(map, man, InactiveProfiler.INSTANCE);
        }

        private Work workFromJsonV1(JsonObject object) {
            Item iconItem = ForgeRegistries.ITEMS.getValue(required(object, "icon"));
            if (iconItem == null) {
                throw new IllegalArgumentException("Icon image does not exist: " + object.get("icon").getAsString());
            }
            Item initReq = ForgeRegistries.ITEMS.getValue(required(object, "initial_request"));
            if (initReq == null) {
                throw new IllegalArgumentException("Initial request item does not exist: " + object.get("icon").getAsString());
            }
            // TODO: Result quantity
            Predicate<Block> isJobBlock = ResourceJobLoader.isJobBlock(object.get("block").getAsString());
            int cooldownTicks = requiredInt(object, "cooldown_ticks");
            WorkWorldInteractions wwi = worldWorkInt(object, cooldownTicks);
            return WorksBehaviour.productionWork(
                    iconItem.getDefaultInstance(),
                    JobID.fromJSON(Util.getOrDefault(object, "id", JsonElement::getAsString, null)),
                    JobID.fromJSON(Util.getOrDefault(object, "parent", JsonElement::getAsString, null)),
                    WorksBehaviour.standardDescription(initReq::getDefaultInstance),
                    new WorkLocation(isJobBlock, required(object, "room")),
                    ResourceJobLoader.workStates(object),
                    wwi,
                    WorksBehaviour.standardProductionRules(),
                    optional(object, "sound")
            );
        }

        private WorkWorldInteractions worldWorkInt(
                JsonObject object,
                int cooldownTicks
        ) {
            if (!object.has("result")) {
                throw new IllegalArgumentException("result is required");
            }
            JsonObject rizz = object.get("result").getAsJsonObject();
            String type = rizz.get("type").getAsString();
            BiFunction<ServerLevel, Collection<MCHeldItem>, Iterable<MCHeldItem>> g = switch (type) {
                case "item" -> (l, i) -> {
                    Item resultItem = ForgeRegistries.ITEMS.getValue(required(rizz, "item"));
                    if (resultItem == null) {
                        throw new IllegalArgumentException("Result item does not exist: " + object.get("icon").getAsString());
                    }
                    ItemStack s = resultItem.getDefaultInstance();
                    int qty = 1;
                    if (rizz.has("quantity")) {
                        qty = rizz.get("quantity").getAsInt();
                    }
                    s.setCount(qty);
                    MCHeldItem mci = MCHeldItem.fromMCItemStack(s);
                    return ImmutableList.of(mci);
                };
                default -> throw new IllegalArgumentException("Unexpected result type: " + type);
            };
            return new WorkWorldInteractions(cooldownTicks, g);
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

        private ResourceLocation required(
                JsonObject object,
                String k
        ) {
            if (!object.has(k)) {
                throw new IllegalArgumentException(k + " is required");
            }
            String iconName = object.get(k).getAsString();
            return new ResourceLocation(iconName);
        }
    }

    private static WorkStates workStates(JsonObject object) {

        ImmutableMap.Builder<Integer, Supplier<Ingredient>> ing = ImmutableMap.builder();
        ImmutableMap.Builder<Integer, Supplier<Integer>> qty = ImmutableMap.builder();
        ImmutableMap.Builder<Integer, Supplier<Ingredient>> tools = ImmutableMap.builder();
        ImmutableMap.Builder<Integer, Supplier<Integer>> work = ImmutableMap.builder();
        ImmutableMap.Builder<Integer, Supplier<Integer>> time = ImmutableMap.builder();

        JsonArray states = object.get("work_states").getAsJsonArray();
        for (int i = 0; i < states.size(); i++) {
            JsonObject v = states.get(i).getAsJsonObject();
            if (v.has("ingredients")) {
                ing.put(i, () -> getIngredient(v.get("ingredients").getAsString()));
            }
            if (v.has("quantity")) {
                qty.put(i, () -> v.get("quantity").getAsInt());
            }
            if (v.has("tools")) {
                tools.put(i, () -> getIngredient(v.get("tools").getAsString()));
            }
            if (v.has("work")) {
                work.put(i, () -> v.get("work").getAsInt());
            }
            if (v.has("time")) {
                time.put(i, () -> v.get("time").getAsInt());
            }
        }
        return new WorkStates(states.size() + 1, ing.build(), qty.build(), tools.build(), work.build(), time.build());
    }

    private static Predicate<Block> isJobBlock(String block) {
        Ingredient ing = getIngredient(block);
        return b -> ing.test(b.asItem().getDefaultInstance());
    }

    private static @NotNull Ingredient getIngredient(String block) {
        if (block.startsWith("#")) {
            return Ingredient.of(TagKey.create(
                    Registry.ITEM_REGISTRY,
                    new ResourceLocation(block.replace("#", ""))
            ));
        }
        return Ingredient.of(ForgeRegistries.ITEMS.getValue(new ResourceLocation(block)));
    }
}

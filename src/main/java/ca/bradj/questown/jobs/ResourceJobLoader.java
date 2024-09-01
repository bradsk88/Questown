package ca.bradj.questown.jobs;

import ca.bradj.questown.QT;
import ca.bradj.questown.core.Config;
import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.jobs.declarative.SoundInfo;
import ca.bradj.questown.jobs.gatherer.GathererTools;
import ca.bradj.questown.jobs.gatherer.Loots;
import ca.bradj.questown.jobs.production.ProductionStatus;
import ca.bradj.questown.mc.Compat;
import ca.bradj.questown.mc.Util;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.profiling.InactiveProfiler;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.*;

public class ResourceJobLoader {

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
                            case 2 -> workFromJsonV2(object);
                            default -> throw new IllegalArgumentException(String.format(
                                    "Unknown job file version \"%s\"", version
                            ));
                        };
                        QT.INIT_LOGGER.info("Work found in filesystem: {}", type.id());
                        QT.INIT_LOGGER.debug("{}: {}", type.id().toNiceString(), type);
                        b.put(type.id(), type);
                    } catch (Exception e) {
                        QT.INIT_LOGGER.error(
                                "Failed to load work {} using version {}",
                                id, optional(object, "version", JsonElement::getAsInt), e
                        );
                        if (Compat.configGet(Config.CRASH_ON_INVALID_JOBS).get()) {
                            throw e;
                        }
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
                throw new IllegalArgumentException("Initial request item does not exist: " + object.get("icon")
                                                                                                   .getAsString());
            }
            BiPredicate<Function<BlockPos, BlockState>, BlockPos> isJobBlock = ResourceJobLoader.isJobBlock(object.get(
                    "block").getAsString());
            int cooldownTicks = requiredInt(object, "cooldown_ticks");
            WorkWorldInteractions wwi = worldWorkInt(object, cooldownTicks);
            return WorksBehaviour.productionWork(
                    iconItem.getDefaultInstance(),
                    JobID.fromJSON(Util.getOrDefault(object, "id", JsonElement::getAsString, null)),
                    JobID.fromJSON(Util.getOrDefault(object, "parent", JsonElement::getAsString, null)),
                    description(initReq, object),
                    new WorkLocation(isJobBlock, required(object, "room")),
                    ResourceJobLoader.workStates(object),
                    wwi,
                    loadRulesV1(object),
                    loadSoundV1(object)
            ).withPriority(requiredInt(object, "priority"));
        }

        private Work workFromJsonV2(JsonObject object) {
            Item iconItem = ForgeRegistries.ITEMS.getValue(required(object, "icon"));
            if (iconItem == null) {
                throw new IllegalArgumentException("Icon image does not exist: " + object.get("icon").getAsString());
            }
            Item initReq = ForgeRegistries.ITEMS.getValue(required(object, "initial_request"));
            if (initReq == null) {
                throw new IllegalArgumentException("Initial request item does not exist: " + object.get("icon")
                                                                                                   .getAsString());
            }
            WorkSpecialRules special = loadRulesV1(object);
            boolean requireAirAbove = special.containsGlobal(SpecialRules.REQUIRE_AIR_ABOVE);
            BiPredicate<Function<BlockPos, BlockState>, BlockPos> isJobBlock = ResourceJobLoader.isJobBlockV2(
                    object.getAsJsonObject("block"),
                    requireAirAbove
            );
            int cooldownTicks = requiredInt(object, "cooldown_ticks");
            WorkWorldInteractions wwi = worldWorkInt(object, cooldownTicks);
            return WorksBehaviour.productionWork(
                    iconItem.getDefaultInstance(),
                    JobID.fromJSON(Util.getOrDefault(object, "id", JsonElement::getAsString, null)),
                    JobID.fromJSON(Util.getOrDefault(object, "parent", JsonElement::getAsString, null)),
                    description(initReq, object),
                    new WorkLocation(isJobBlock, required(object, "room")),
                    ResourceJobLoader.workStates(object),
                    wwi,
                    special,
                    loadSoundV1(object)
            ).withPriority(requiredInt(object, "priority"));
        }

        private WorkSpecialRules loadRulesV1(JsonObject object) {
            ImmutableList.Builder<String> globals = ImmutableList.builder();
            globals.add(
                    SpecialRules.PRIORITIZE_EXTRACTION,
                    SpecialRules.SHARED_WORK_STATUS
            );

            if (!object.has("special")) {
                return new WorkSpecialRules(
                        ImmutableMap.of(),
                        globals.build()
                );
            }

            Map<ProductionStatus, Collection<String>> stages = new HashMap<>();
            object.get("special").getAsJsonArray().forEach(row -> {
                JsonObject rowObj = row.getAsJsonObject();
                JsonArray rules = rowObj.get("rules").getAsJsonArray();
                rules.forEach(rule -> registerRule(rule, rowObj, globals, stages));
            });


            return new WorkSpecialRules(
                    ImmutableMap.copyOf(stages),
                    globals.build()
            );
        }

        private @Nullable SoundInfo loadSoundV1(JsonObject object) {
            if (!object.has("sound")) {
                return null;
            }
            JsonObject info = object.getAsJsonObject("sound");
            ResourceLocation rl = new ResourceLocation(info.get("id").getAsString());
            @Nullable Integer chance = null;
            if (info.has("chance")) {
                chance = info.get("chance").getAsInt();
            }
            @Nullable Integer duration = null;
            if (info.has("duration")) {
                duration = info.get("duration").getAsInt();
            }

            return new SoundInfo(rl, chance, duration);
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
                case "item" -> itemResult(object, rizz);
                case "biome_loot" -> biomeLootResult(rizz);
                case "crafting_table" -> craftingTableResult(rizz);
                default -> throw new IllegalArgumentException("Unexpected result type: " + type);
            };
            return new WorkWorldInteractions(cooldownTicks, g);
        }

    }

    private static @NotNull WorkDescription description(
            @Nullable Item initReq,
            JsonObject object
    ) {
        if (!object.has("result")) {
            throw new IllegalArgumentException("result is required");
        }
        JsonObject rizz = object.get("result").getAsJsonObject();
        String type = rizz.get("type").getAsString();

        return switch (type) {
            case "biome_loot" -> biomeDesc(initReq, rizz);
            default -> WorksBehaviour.standardDescription(initReq == null ? () -> null : initReq::getDefaultInstance);
        };

    }

    private static @NotNull WorkDescription biomeDesc(
            @Nullable Item initReq,
            JsonObject rizz
    ) {
        String resultPrefix = required(rizz, "prefix", JsonElement::getAsString);
        GathererTools.LootTablePrefix lootTablePrefix = new GathererTools.LootTablePrefix(resultPrefix);
        return new WorkDescription(
                t -> t.allKnownGatherItemsFn().apply(lootTablePrefix),
                initReq == null ? null : initReq.getDefaultInstance()
        );
    }

    private static void registerRule(
            JsonElement rule,
            JsonObject rowObj,
            ImmutableList.Builder<String> globals,
            Map<ProductionStatus, Collection<String>> writeableStages
    ) throws NotValidCoreStatus {
        String type = rowObj.get("type").getAsString();
        switch (type) {
            case "global": {
                globals.add(rule.getAsString());
                break;
            }
            case "processing_state": {
                int state = requiredInt(rowObj, "state");
                Util.addOrInitialize(writeableStages, ProductionStatus.fromJobBlockStatus(state), rule.getAsString());
                break;
            }
            case "core_state": {
                String name = required(rowObj, "state", JsonElement::getAsString);
                ImmutableSet<ProductionStatus> all = ProductionStatus.allStatuses();
                ProductionStatus productionStatus = all.stream()
                                                       .filter(v -> v.name.equals(name))
                                                       .findFirst()
                                                       .orElseThrow(() -> new NotValidCoreStatus(name, all));
                Util.addOrInitialize(writeableStages, productionStatus, rule.getAsString());
                break;
            }
            default:
                throw new IllegalArgumentException("Unexpected special type " + type);
        }
    }

    private static @NotNull BiFunction<ServerLevel, Collection<MCHeldItem>, Iterable<MCHeldItem>> itemResult(
            JsonObject object,
            JsonObject rizz
    ) {
        Item resultItem = ForgeRegistries.ITEMS.getValue(required(rizz, "item"));
        return (l, i) -> {
            if (resultItem == null) {
                throw new IllegalArgumentException("Result item does not exist: " + object.get("icon")
                                                                                          .getAsString());
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
    }

    private static @NotNull BiFunction<ServerLevel, Collection<MCHeldItem>, Iterable<MCHeldItem>> biomeLootResult(
            JsonObject rizz
    ) {
        String resultPrefix = required(rizz, "prefix", JsonElement::getAsString);
        String resultDefault = required(rizz, "default", JsonElement::getAsString);
        int resultAttempts = requiredInt(rizz, "attempts");
        return (l, i) -> Loots.getFromLootTables(
                l,
                i,
                resultAttempts,
                new GathererTools.LootTableParameters(
                        new GathererTools.LootTablePrefix(resultPrefix),
                        new GathererTools.LootTablePath(resultDefault)
                )
        );
    }

    private static @NotNull BiFunction<ServerLevel, Collection<MCHeldItem>, Iterable<MCHeldItem>> craftingTableResult(
            JsonObject rizz
    ) {
        JsonArray resultPrefix = required(rizz, "recipe", JsonElement::getAsJsonArray);
        @Nullable String fallback = optional(rizz, "fallback", JsonElement::getAsString);
        int qty = requiredInt(rizz, "quantity");
        return (l, i) -> {
            ItemStack itemstack = ItemStack.EMPTY;
            CraftingContainer cc = new CraftingContainer(new AbstractContainerMenu(null, 0) {
                @Override
                public boolean stillValid(Player p_38874_) {
                    return false;
                }
            }, 3, 3);

            for (int j = 0; j < resultPrefix.size(); j++) {
                String rowStr = resultPrefix.get(j).getAsString();
                for (int k = 0; k < rowStr.length(); k++) {
                    if (Character.isWhitespace(rowStr.charAt(k))) {
                        continue;
                    }
                    ItemStack itemFromChar = Items.OAK_LOG.getDefaultInstance();
                    cc.setItem((j + 1) * k, itemFromChar);
                }
            }

            Optional<CraftingRecipe> optional = l.getServer().getRecipeManager()
                                                 .getRecipeFor(RecipeType.CRAFTING, cc, l);
            if (optional.isPresent()) {
                CraftingRecipe craftingrecipe = optional.get();
                itemstack = craftingrecipe.assemble(cc);
            }
            if (itemstack.isEmpty() && fallback != null) {
                itemstack = ForgeRegistries.ITEMS.getValue(new ResourceLocation(fallback)).getDefaultInstance();
            }
            itemstack.setCount(1);
            return ImmutableList.copyOf(Collections.nCopies(qty, MCHeldItem.fromMCItemStack(itemstack)));
        };
    }

    private static WorkStates workStates(JsonObject object) {

        ImmutableMap.Builder<Integer, Supplier<Ingredient>> ing = ImmutableMap.builder();
        Map<Integer, Supplier<Integer>> qty = new HashMap<>();
        ImmutableMap.Builder<Integer, Supplier<Ingredient>> tools = ImmutableMap.builder();
        ImmutableMap.Builder<Integer, Supplier<Integer>> work = ImmutableMap.builder();
        ImmutableMap.Builder<Integer, Supplier<Integer>> time = ImmutableMap.builder();

        JsonArray states = object.get("work_states").getAsJsonArray();
        int maxState = 0;
        for (int i = 0; i < states.size(); i++) {
            JsonObject v = states.get(i).getAsJsonObject();
            if (v.has("ingredients")) {
                Ingredient ingredients = getIngredient(v.get("ingredients").getAsString());
                ing.put(i, () -> ingredients);
                maxState = Math.max(maxState, i + 1);
                Util.putIfAbsent(qty, i, () -> 1);
            }
            if (v.has("quantity")) {
                int quantity = v.get("quantity").getAsInt();
                qty.put(i, () -> quantity);
                maxState = Math.max(maxState, i + 1);
            }
            if (v.has("tools")) {
                Ingredient tools1 = getIngredient(v.get("tools").getAsString());
                tools.put(i, () -> tools1);
                maxState = Math.max(maxState, i + 1);
            }
            if (v.has("work")) {
                int work1 = v.get("work").getAsInt();
                work.put(i, () -> work1);
                maxState = Math.max(maxState, i + 1);
            }
            if (v.has("time")) {
                int time1 = v.get("time").getAsInt();
                time.put(i, () -> time1);
                maxState = Math.max(maxState, i + 1);
            }
        }
        return new WorkStates(
                maxState,
                ing.build(),
                ImmutableMap.copyOf(qty),
                tools.build(),
                work.build(),
                time.build()
        );
    }

    private static BiPredicate<Function<BlockPos, BlockState>, BlockPos> isJobBlock(String block) {
        Ingredient ing = getIngredient(block);
        return (sl, bp) -> ing.test(sl.apply(bp).getBlock().asItem().getDefaultInstance());
    }


    private static Optional<Integer> getStateValue(
            BlockState state,
            String name
    ) {
        return state.getValues().entrySet().stream()
                    .filter(v -> v.getKey().getName().equals(name))
                    .filter(v -> v.getKey().getValueClass().equals(Integer.class))
                    .map(v -> (Integer) v.getValue())
                    .findFirst();
    }

    private static BiPredicate<Function<BlockPos, BlockState>, BlockPos> isJobBlockV2(
            JsonObject block,
            boolean requireAirAbove
    ) {
        Predicate<BlockState> baseTest = getBlockCheck(required(block, "id", JsonElement::getAsString));

        Optional<BlockStateComparator> stateComparator = getStateComparator(block);

        return (sl, bp) -> {
            if (requireAirAbove && !sl.apply(bp.above()).isAir()) {
                return false;
            }
            BlockState state = sl.apply(bp);
            if (!baseTest.test(state)) {
                return false;
            }
            return stateComparator.map(comparator -> comparator.test(state)).orElse(true);
        };
    }

    private static Optional<BlockStateComparator> getStateComparator(JsonObject block) {
        String stateStr = optional(block, "int_state", JsonElement::getAsString);
        return stateStr == null ? Optional.empty() : BlockStateComparator.parse(stateStr);
    }

    private static class BlockStateComparator {
        private final String name;
        private final Function<Integer, Boolean> compare;

        public BlockStateComparator(
                String name,
                Function<Integer, Boolean> compare
        ) {
            this.name = name;
            this.compare = compare;
        }

        public boolean test(BlockState state) {
            return getStateValue(state, name)
                    .map(compare)
                    .orElse(true);
        }

        public static Optional<BlockStateComparator> parse(String stateStr) {
            String[] eq = stateStr.split("=");
            if (eq.length > 1) {
                return Optional.of(new BlockStateComparator(eq[0], value -> value.equals(Integer.parseInt(eq[1]))));
            } else {
                String[] lt = stateStr.split("<");
                return Optional.of(new BlockStateComparator(
                        lt[0],
                        value -> value.compareTo(Integer.parseInt(lt[1])) < 0
                ));
            }
        }
    }

    private static @NotNull Ingredient getIngredient(String block) {
        Ingredient ingredient = doGetIngredient(block);
        if (ingredient.isEmpty()) {
            throw new IllegalArgumentException(block + " is an unknown or empty ingredient");
        }
        return ingredient;
    }

    private static @NotNull Ingredient doGetIngredient(String block) {
        if (block.startsWith("#")) {
            return Ingredient.of(TagKey.create(
                    Registry.ITEM_REGISTRY,
                    new ResourceLocation(block.replace("#", ""))
            ));
        }
        return Ingredient.of(ForgeRegistries.ITEMS.getValue(new ResourceLocation(block)));
    }

    private static @NotNull Predicate<BlockState> getBlockCheck(String block) {
        if (block.startsWith("#")) {
            TagKey<Block> tag = BlockTags.create(new ResourceLocation(block.replace("#", "")));
            return bs -> ForgeRegistries.BLOCKS.tags().getTag(tag).contains(bs.getBlock());
        }
        return (BlockState bs) -> {
            ResourceLocation resourceLocation = new ResourceLocation(block);
            return resourceLocation.equals(bs.getBlock().getRegistryName());
        };
    }

    private static @Nullable <X> X optional(
            JsonObject object,
            String k,
            Function<JsonElement, X> puller
    ) {
        if (!object.has(k)) {
            return null;
        }
        return puller.apply(object.get(k));
    }

    private static int requiredInt(
            JsonObject object,
            String k
    ) {
        return required(object, k, JsonElement::getAsInt);
    }

    private static <X> X required(
            JsonObject object,
            String k,
            Function<JsonElement, X> puller
    ) {
        if (!object.has(k)) {
            throw new IllegalArgumentException(k + " is required");
        }
        return puller.apply(object.get(k));
    }

    private static ResourceLocation required(
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

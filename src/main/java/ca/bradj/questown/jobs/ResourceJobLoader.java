package ca.bradj.questown.jobs;

import ca.bradj.questown.QT;
import ca.bradj.questown.Questown;
import ca.bradj.questown.core.Config;
import ca.bradj.questown.core.Pair;
import ca.bradj.questown.core.UtilClean;
import ca.bradj.questown.gui.Ingredients;
import ca.bradj.questown.integration.jobs.JobPhaseModifier;
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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.profiling.InactiveProfiler;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.Container;
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
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.LootTables;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

// TODO: When a job requires a block, and a room which does not include that block - raise a warning

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
                                    "Unknown job file version \"%s\"",
                                    version
                            ));
                        };
                        QT.INIT_LOGGER.info("Work found in filesystem: {}", type.id);
                        QT.INIT_LOGGER.debug("{}: {}", type.id.toNiceString(), type);
                        b.put(type.id, type);
                    } catch (Exception e) {
                        QT.INIT_LOGGER.error(
                                "Failed to load work {} using version {}",
                                id,
                                optional(object, "version", JsonElement::getAsInt),
                                e
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
            ResourceLocation initialRequest = optional(
                    object,
                    "initial_request",
                    el -> el.isJsonNull() ? null : new ResourceLocation(el.getAsString())
            );
            Item initReq = null;
            if (initialRequest != null) {
                initReq = ForgeRegistries.ITEMS.getValue(initialRequest);
                if (initReq == null) {
                    throw new IllegalArgumentException("Initial request item does not exist: " + object.get("icon")
                                                                                                       .getAsString());
                }
            }
            BiPredicate<WorkLocation.BlockInfo, BlockPos> isJobBlock = ResourceJobLoader.isJobBlock(object.get("block")
                                                                                                          .getAsString());
            int cooldownTicks = requiredInt(object, "cooldown_ticks");
            WorkWorldInteractions wwi = worldWorkInt(object, cooldownTicks);
            JobID id = JobID.fromJSON(Util.getOrDefault(object, "id", JsonElement::getAsString, null));
            Work wb = WorksBehaviour.productionWork(
                    iconItem.getDefaultInstance(),
                    id,
                    JobID.fromJSON(Util.getOrDefault(object, "parent", JsonElement::getAsString, null)),
                    description(initReq, object),
                    new WorkLocation(
                            (ctx) -> isJobBlock.test(ctx.blockInfo(), ctx.blockPos()),
                            isJobBlock,
                            required(object, "room")
                    ),
                    ResourceJobLoader.workStates(id, object),
                    wwi,
                    loadRulesV1(object),
                    loadSoundV1(object)
            ).withPriority(requiredInt(object, "priority"));
            @Nullable Overrides overrides = overridesFromJsonV2(object);
            if (overrides != null) {
                return wb.withOverrides(overrides);
            }
            return wb;
        }

        private @Nullable Overrides overridesFromJsonV2(JsonObject object) {
            ImmutableMap<IStatus<?>, ResourceLocation> textureOverrides = textureOverridesV2(object);
            ImmutableMap<IStatus<?>, Pair<String, String>> statusTextOverrides = statusTextOverridesV2(object);
            return new Overrides(textureOverrides, statusTextOverrides);
        }

        private ImmutableMap<IStatus<?>, ResourceLocation> textureOverridesV2(JsonObject object) {
            ImmutableMap.Builder<IStatus<?>, ResourceLocation> textureOverrides = ImmutableMap.builder();
            if (object.has("status_texture_overrides")) {
                JsonArray rows = object.getAsJsonArray("status_texture_overrides");
                for (JsonElement row : rows) {
                    JsonObject rowObj = row.getAsJsonObject();
                    String type = rowObj.get("type").getAsString();
                    switch (type) {
                        case "core_state": {
                            ProductionStatus productionStatus = getCore(rowObj);
                            String newTexture = rowObj.get("new_texture").getAsString();
                            @NotNull String[] newTextureParts = newTexture.split(":");
                            if (newTextureParts.length == 2) {
                                ResourceLocation rl = new ResourceLocation(newTextureParts[0], newTextureParts[1]);
                                textureOverrides.put(productionStatus, rl);
                            } else {
                                textureOverrides.put(productionStatus, Questown.ResourceLocation(newTexture));
                            }
                            break;
                        }
                        default:
                            throw new IllegalArgumentException("Unexpected texture override type " + type);
                    }
                }
            }
            return textureOverrides.build();
        }

        private ImmutableMap<IStatus<?>, Pair<String, String>> statusTextOverridesV2(JsonObject object) {
            ImmutableMap.Builder<IStatus<?>, Pair<String, String>> textOverrides = ImmutableMap.builder();
            if (object.has("status_text_overrides")) {
                JsonArray rows = object.getAsJsonArray("status_text_overrides");
                for (JsonElement row : rows) {
                    JsonObject rowObj = row.getAsJsonObject();
                    String type = rowObj.get("type").getAsString();
                    switch (type) {
                        case "core_state": {
                            ProductionStatus productionStatus = getCore(rowObj);
                            String newKey1 = rowObj.get("new_key_1").getAsString();
                            String newKey2 = rowObj.get("new_key_2").getAsString();
                            textOverrides.put(productionStatus, new Pair<>(newKey1, newKey2));
                            break;
                        }
                        default:
                            throw new IllegalArgumentException("Unexpected status text override type " + type);
                    }
                }
            }
            return textOverrides.build();
        }

        private Work workFromJsonV2(JsonObject obj) {
            Item iconItem = ForgeRegistries.ITEMS.getValue(required(obj, "icon"));
            if (iconItem == null) {
                throw new IllegalArgumentException("Icon image does not exist: " + obj.get("icon").getAsString());
            }
            ResourceLocation initialRequest = optional(
                    obj,
                    "initial_request",
                    el -> el.isJsonNull() ? null : new ResourceLocation(el.getAsString())
            );
            Item initReq = ForgeRegistries.ITEMS.getValue(initialRequest);
            if (initReq == null) {
                throw new IllegalArgumentException("Initial request item does not exist: " + obj.get("icon")
                                                                                                .getAsString());
            }
            WorkSpecialRules special = loadRulesV2(obj);
            if (!obj.get("block").isJsonObject()) {
                throw new IllegalArgumentException("block must be an object");
            }

            try {
                JsonObject block = obj.getAsJsonObject("block");
                Predicate<JobBlockTestContext> isJobBlock = ResourceJobLoader.isJobBlockV2(
                        block,
                        special
                );
                int cooldownTicks = requiredInt(obj, "cooldown_ticks");
                WorkWorldInteractions wwi = worldWorkInt(obj, cooldownTicks);
                JobID id = JobID.fromJSON(Util.getOrDefault(obj, "id", JsonElement::getAsString, null));
                BiPredicate<WorkLocation.BlockInfo, BlockPos> shouldInitWS = shouldInitWS(block, special);
                return WorksBehaviour.productionWork(
                        iconItem.getDefaultInstance(),
                        id,
                        JobID.fromJSON(Util.getOrDefault(obj, "parent", JsonElement::getAsString, null)),
                        description(initReq, obj),
                        new WorkLocation(isJobBlock, shouldInitWS, required(obj, "room")),
                        ResourceJobLoader.workStates(id, obj),
                        wwi,
                        special,
                        loadSoundV1(obj)
                ).withPriority(requiredInt(obj, "priority"));
            } catch (Exception e) {
                throw new IllegalArgumentException("Failed to parse block: " + e.getMessage(), e);
            }
        }

        private WorkSpecialRules loadRulesV1(JsonObject object) {
            ImmutableList.Builder<String> globals = ImmutableList.builder();
            globals.add(SpecialRules.PRIORITIZE_EXTRACTION);

            if (!object.has("special")) {
                return new WorkSpecialRules(ImmutableMap.of(), globals.build());
            }

            Map<ProductionStatus, List<String>> stages = new HashMap<>();
            object.get("special").getAsJsonArray().forEach(row -> {
                JsonObject rowObj = row.getAsJsonObject();
                JsonArray rules = rowObj.get("rules").getAsJsonArray();
                rules.forEach(rule -> registerRule(rule, rowObj, globals, stages));
            });


            return new WorkSpecialRules(ImmutableMap.copyOf(stages), globals.build());
        }

        private WorkSpecialRules loadRulesV2(JsonObject object) {
            ImmutableList.Builder<String> globals = ImmutableList.builder();
            globals.add(SpecialRules.PRIORITIZE_EXTRACTION);

            if (!object.has("special")) {
                return new WorkSpecialRules(ImmutableMap.of(), globals.build());
            }

            Map<ProductionStatus, List<String>> stages = new HashMap<>();
            object.get("special").getAsJsonArray().forEach(row -> {
                JsonObject rowObj = row.getAsJsonObject();
                JsonArray rules = rowObj.get("rules").getAsJsonArray();
                rules.forEach(rule -> registerRule(rule, rowObj, globals, stages));
            });

            return new WorkSpecialRules(ImmutableMap.copyOf(stages), globals.build());
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
            ResultGenerator<MCHeldItem> g = switch (type) {
                case "item" -> itemResult(object, rizz);
                case "biome_loot" -> biomeLootResult(rizz);
                case "loot" -> lootResult(rizz);
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
        // TODO: Validate that the initial request is present in the loot table
        return new WorkDescription(
                t -> t.allKnownGatherItemsFn().apply(lootTablePrefix),
                initReq == null ? null : initReq.getDefaultInstance()
        );
    }

    private static void registerRule(
            JsonElement rule,
            JsonObject rowObj,
            ImmutableList.Builder<String> globals,
            Map<ProductionStatus, List<String>> writeableStages
    ) throws NotValidCoreStatus {
        String type = rowObj.get("type").getAsString();
        switch (type) {
            case "global": {
                globals.add(rule.getAsString());
                break;
            }
            case "processing_state": {
                int state = requiredInt(rowObj, "state");
                UtilClean.addOrInitializeList(
                        writeableStages,
                        ProductionStatus.fromJobBlockStatus(state),
                        rule.getAsString()
                );
                break;
            }
            case "core_state": {
                ProductionStatus productionStatus = getCore(rowObj);
                UtilClean.addOrInitializeList(writeableStages, productionStatus, rule.getAsString());
                break;
            }
            default:
                throw new IllegalArgumentException("Unexpected special type " + type);
        }
    }

    private static ProductionStatus getCore(JsonObject rowObj) {
        String name = required(rowObj, "state", JsonElement::getAsString);
        ImmutableSet<ProductionStatus> all = ProductionStatus.allStatuses();
        ProductionStatus productionStatus = all.stream().filter(v -> v.name.equals(name)).findFirst()
                                               .orElseThrow(() -> new NotValidCoreStatus(name, all));
        return productionStatus;
    }

    private static @NotNull ResultGenerator<MCHeldItem> itemResult(
            JsonObject object,
            JsonObject rizz
    ) {
        Item resultItem = ForgeRegistries.ITEMS.getValue(required(rizz, "item"));
        return new ResultGenerator<>() {
            @Override
            public Iterable<MCHeldItem> generate(
                    ServerLevel level,
                    Collection<MCHeldItem> heldItems
            ) {
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
            }

            @Override
            public boolean isResultAlwaysEmpty() {
                return resultItem == null || resultItem.getDefaultInstance().isEmpty();
            }
        };
    }

    private static @NotNull ResultGenerator<MCHeldItem> biomeLootResult(
            JsonObject rizz
    ) {
        String resultPrefix = required(rizz, "prefix", JsonElement::getAsString);
        String resultDefault = required(rizz, "default", JsonElement::getAsString);
        int resultAttempts = requiredInt(rizz, "attempts");
        return new ResultGenerator<MCHeldItem>() {
            @Override
            public Iterable<MCHeldItem> generate(
                    ServerLevel level,
                    Collection<MCHeldItem> heldItems
            ) {
                return Loots.getFromLootTables(
                        level,
                        heldItems,
                        resultAttempts,
                        new GathererTools.LootTableParameters(
                                new GathererTools.LootTablePrefix(resultPrefix),
                                new GathererTools.LootTablePath(resultDefault)
                        )
                );
            }

            @Override
            public boolean isResultAlwaysEmpty() {
                return false;
            }
        };
    }

    private static @NotNull ResultGenerator<MCHeldItem> lootResult(
            JsonObject rizz
    ) {
        String table = required(rizz, "table", JsonElement::getAsString);
        int maxResults = requiredInt(rizz, "max_results");
        return new ResultGenerator<MCHeldItem>() {
            @Override
            public Iterable<MCHeldItem> generate(
                    ServerLevel level,
                    Collection<MCHeldItem> heldItems
            ) {
                LootTables tables = level.getServer().getLootTables();
                LootTable loot = tables.get(ResourceLocation.tryParse(table));
                return Loots.loadFromTables(level, loot, 1, maxResults).stream().map(MCHeldItem::fromTown).toList();
            }

            @Override
            public boolean isResultAlwaysEmpty() {
                return false;
            }
        };
    }

    private static @NotNull ResultGenerator<MCHeldItem> craftingTableResult(
            JsonObject rizz
    ) {
        JsonArray ingredientChar = required(rizz, "recipe", JsonElement::getAsJsonArray);
        @Nullable String fallback = optional(rizz, "fallback", JsonElement::getAsString);
        int qty = requiredInt(rizz, "quantity");
        return new ResultGenerator<MCHeldItem>() {
            @Override
            public Iterable<MCHeldItem> generate(
                    ServerLevel l,
                    Collection<MCHeldItem> heldItems
            ) {
                ItemStack itemstack = ItemStack.EMPTY;
                CraftingContainer cc = new CraftingContainer(
                        new AbstractContainerMenu(null, 0) {
                            @Override
                            public ItemStack quickMoveStack(
                                    Player player,
                                    int i
                            ) {
                                return ItemStack.EMPTY;
                            }

                            @Override
                            public boolean stillValid(Player p_38874_) {
                                return false;
                            }
                        }, 3, 3
                );

                for (int j = 0; j < ingredientChar.size(); j++) {
                    String rowStr = ingredientChar.get(j).getAsString();
                    for (int k = 0; k < rowStr.length(); k++) {
                        if (Character.isWhitespace(rowStr.charAt(k))) {
                            continue;
                        }
                        // TODO: Actually get the item from the character
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

            }

            @Override
            public boolean isResultAlwaysEmpty() {
                return false;
            }
        };
    }

    private static WorkStates workStates(
            JobID id,
            JsonObject object
    ) {

        ImmutableMap.Builder<Integer, Supplier<Ingredient>> ing = ImmutableMap.builder();
        Map<Integer, Supplier<Integer>> qty = new HashMap<>();
        ImmutableMap.Builder<Integer, Supplier<Ingredient>> tools = ImmutableMap.builder();
        ImmutableMap.Builder<Integer, Supplier<Integer>> work = ImmutableMap.builder();
        ImmutableMap.Builder<Integer, Supplier<Integer>> time = ImmutableMap.builder();

        JsonArray states = object.get("work_states").getAsJsonArray();

        int maxState = 0;
        for (int i = 0; i < states.size(); i++) {
            JsonObject v = states.get(i).getAsJsonObject();
            boolean valid = false;
            if (v.has("ingredients")) {
                Ingredient ingredients = getIngredient(v.get("ingredients").getAsString());
                ing.put(i, () -> ingredients);
                maxState = Math.max(maxState, i + 1);
                Util.putIfAbsent(qty, i, () -> 1);
                valid = true;
            }
            if (v.has("quantity")) {
                int quantity = v.get("quantity").getAsInt();
                qty.put(i, () -> quantity);
                maxState = Math.max(maxState, i + 1);
                valid = true;
            }
            if (v.has("tools")) {
                Ingredient tools1 = getIngredient(v.get("tools").getAsString());
                tools.put(i, () -> tools1);
                maxState = Math.max(maxState, i + 1);
                valid = true;
            }
            if (v.has("work")) {
                int work1 = v.get("work").getAsInt();
                work.put(i, () -> work1);
                maxState = Math.max(maxState, i + 1);
                valid = valid || i != 0; // ONLY work is not sufficient for state 0
            }
            if (v.has("time")) {
                int time1 = v.get("time").getAsInt();
                time.put(i, () -> time1);
                maxState = Math.max(maxState, i + 1);
                valid = true;
            }
            if (!valid) {
                String fmt = "Job %s is missing ingredients, tools, work, or time for state at index %d: %s";
                if (i == 0) {
                    fmt = "Job %s is missing ingredients, tools, or time for state at index %d: %s";
                }
                throw new IllegalJobDefinition(String.format(fmt, id.toNiceString(), i, v));
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

    private static BiPredicate<WorkLocation.BlockInfo, BlockPos> isJobBlock(String block) {
        Ingredient ing = getIngredient(block);
        return (sl, bp) -> ing.test(sl.state(bp).getBlock().asItem().getDefaultInstance());
    }


    private static Optional<Integer> getStateValue(
            BlockState state,
            String name
    ) {
        return state.getValues().entrySet().stream().filter(v -> v.getKey().getName().equals(name))
                    .filter(v -> v.getKey().getValueClass().equals(Integer.class)).map(v -> (Integer) v.getValue())
                    .findFirst();
    }

    private static Optional<ItemStack> getSlotValue(
            BlockEntity state,
            int slot
    ) {
        if (!(state instanceof Container c)) {
            return Optional.empty();
        }
        return Optional.of(c.getItem(slot));
    }

    private static BiPredicate<WorkLocation.BlockInfo, BlockPos> shouldInitWS(
            JsonObject block,
            WorkSpecialRules special
    ) {
        Predicate<BlockState> baseTest = getBlockCheck(required(block, "id", JsonElement::getAsString));

        Optional<BlockStateComparator> stateComparator = getStateComparator(block);
        Optional<BlockSlotComparator> slotComparator = getSlotComparator(block);

        boolean requireAirAbove = special.containsGlobal(SpecialRules.REQUIRE_AIR_ABOVE);

        return (i, p) -> {
            if (requireAirAbove && !i.state(p.above()).isAir()) {
                return false;
            }
            BlockState state = i.state(p);
            return baseTest.test(state);
        };
    }

    private static Predicate<JobBlockTestContext> isJobBlockV2(
            JsonObject block,
            WorkSpecialRules special
    ) {
        Predicate<BlockState> baseTest = getBlockCheck(required(block, "id", JsonElement::getAsString));

        Optional<BlockStateComparator> stateComparator = getStateComparator(block);
        Optional<BlockSlotComparator> slotComparator = getSlotComparator(block);

        boolean requireAirAbove = special.containsGlobal(SpecialRules.REQUIRE_AIR_ABOVE);

        return (ctx) -> {
            if (requireAirAbove && !ctx.blockInfo().state(ctx.blockPos().above()).isAir()) {
                return false;
            }
            BlockState state = ctx.blockInfo().state(ctx.blockPos());
            if (!baseTest.test(state)) {
                return false;
            }
            Boolean stateCheck = stateComparator.map(comparator -> comparator.test(state)).orElse(true);
            if (!stateCheck) {
                return false;
            }

            if (ctx.jobBlockAlreadyUsed()) {
                // Often, jobs that use the slot comparator also modify the slot contents
                // This modified state can cause the slot check to fail, resulting in a
                // sort of deadlock caused by the villager themselves.
                return true;
            }

            BlockEntity entity = ctx.blockInfo().entity(ctx.blockPos());
            boolean slotOk = slotComparator.map(comparator -> comparator.test(entity)).orElse(true);
            if (!slotOk) {
                return false;
            }
            // All checks passed, now try special rule
            return trySpecialRuleIsJobBlock(ctx, special);
        };
    }

    /**
     * Calls out to special rule appliers if all other isJobBlock checks fail.
     * Allows mod integrators to provide custom logic for job block validation.
     */
    private static boolean trySpecialRuleIsJobBlock(
            JobBlockTestContext ctx,
            WorkSpecialRules special
    ) {
        // Gather all global and per-status rules
        ImmutableList<String> allRules = ImmutableList.<String>builder().addAll(special.specialGlobalRules()).build();
        // Query all registered JobPhaseModifier appliers
        boolean passed = false;
        for (JobPhaseModifier rule : ca.bradj.questown.integration.SpecialRulesRegistry.getRuleAppliers(allRules)) {
            if (rule.postJobBlockCheckPassed(ctx)) {
                passed = true;
                continue;
            }
            return false;
        }
        return passed;
    }

    private static Optional<BlockStateComparator> getStateComparator(JsonObject block) {
        String stateStr = optional(block, "int_state", JsonElement::getAsString);
        return stateStr == null ? Optional.empty() : BlockStateComparator.parse(stateStr);
    }

    private static Optional<BlockSlotComparator> getSlotComparator(JsonObject block) {
        String stateStr = optional(block, "has_item_in_slot_initially", JsonElement::getAsString);
        return stateStr == null ? Optional.empty() : BlockSlotComparator.parse(stateStr);
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
            return getStateValue(state, name).map(compare).orElse(true);
        }

        public static Optional<BlockStateComparator> parse(String stateStr) {
            String[] eq = stateStr.split("=");
            if (eq.length > 1) {
                return Optional.of(new BlockStateComparator(eq[0], value -> value.equals(Integer.parseInt(eq[1]))));
            }
            String[] lt = stateStr.split("<");
            if (lt.length > 1) {
                return Optional.of(new BlockStateComparator(
                        lt[0],
                        value -> value.compareTo(Integer.parseInt(lt[1])) < 0
                ));
            }

            String[] gt = stateStr.split(">");
            if (gt.length > 1) {
                return Optional.of(new BlockStateComparator(
                        gt[0],
                        value -> value.compareTo(Integer.parseInt(gt[1])) > 0
                ));
            }
            return Optional.empty();
        }
    }

    private static class BlockSlotComparator {
        private final int slotIndex;
        private final Function<ItemStack, Boolean> compare;

        public BlockSlotComparator(
                int slot,
                Function<ItemStack, Boolean> compare
        ) {
            this.slotIndex = slot;
            this.compare = compare;
        }

        public boolean test(BlockEntity entity) {
            return getSlotValue(entity, slotIndex).map(compare).orElse(true);
        }

        public static Optional<BlockSlotComparator> parse(String stateStr) {
            String[] eq = stateStr.split("/");
            if (eq.length > 1) {
                int slot = Integer.parseInt(eq[0]);
                // Not using getIngredient because "empty" is valid here
                Predicate<ItemStack> check = ItemStack::isEmpty;
                if (!eq[1].equals("minecraft:air")) {
                    check = Ingredients.fromString(eq[1]);
                }
                return Optional.of(new BlockSlotComparator(slot, check::test));
            }

            return Optional.empty();
        }
    }

    private static @NotNull Ingredient getIngredient(String block) {
        Ingredient ingredient = Ingredients.fromString(block);
        if (ingredient.isEmpty()) {
            throw new IllegalArgumentException(block + " is an unknown or empty ingredient");
        }
        return ingredient;
    }

    private static @NotNull Predicate<BlockState> getBlockCheck(String block) {
        if (block.startsWith("#")) {
            TagKey<Block> tag = BlockTags.create(new ResourceLocation(block.replace("#", "")));
            return bs -> ForgeRegistries.BLOCKS.tags().getTag(tag).contains(bs.getBlock());
        }
        return (BlockState bs) -> {
            ResourceLocation resourceLocation = new ResourceLocation(block);
            return resourceLocation.equals(Compat.getItemId(bs.getBlock()));
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
            throw new IllegalArgumentException(k + " is required (missing)");
        }
        JsonElement t = object.get(k);
        if (t.isJsonNull()) {
            throw new IllegalArgumentException(k + " is required (null)");
        }
        return puller.apply(t);
    }

    private static ResourceLocation required(
            JsonObject object,
            String k
    ) {
        String v = required(object, k, JsonElement::getAsString);
        return new ResourceLocation(v);
    }
}

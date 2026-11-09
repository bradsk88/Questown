package ca.bradj.questown.mc;

import ca.bradj.questown.core.Coordinate;
import ca.bradj.questown.town.entity.TownFlagBlockEntity;
import ca.bradj.questown.town.rooms.TownPosition;
import ca.bradj.questown.world.QTToolAction;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.RandomSource;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import net.neoforged.neoforge.common.ToolAction;
import net.neoforged.neoforge.common.ToolActions;

/**
 * Compat executes the "strangler" pattern. Putting all code that changes
 * between minecraft versions in a centralized location to minimize the
 * effort required for forward and back-porting this mod between versions.
 */
public class Compat {
    public static final RandomSource RANDOM = RandomSource.create();

    /** The mouse position in gui-scaled screen coordinates. */
    public static double guiMouseX() {
        Minecraft mc = Minecraft.getInstance();
        return mc.mouseHandler.xpos() * mc.getWindow().getGuiScaledWidth() / mc.getWindow().getScreenWidth();
    }

    public static double guiMouseY() {
        Minecraft mc = Minecraft.getInstance();
        return mc.mouseHandler.ypos() * mc.getWindow().getGuiScaledHeight() / mc.getWindow().getScreenHeight();
    }

    public static void playNeutralSound(
            ServerLevel serverLevel,
            BlockPos pos,
            SoundEvent sound
    ) {
        float volume = 0.5f;
        float pitchUpOrDown = 1.0F + (serverLevel.random.nextFloat() - serverLevel.random.nextFloat()) * 0.4F;
        serverLevel.playSound(
                null,
                pos,
                sound,
                SoundSource.NEUTRAL,
                volume,
                pitchUpOrDown
        );
    }

    /**
     * Vanilla's cave ambience: a random one of its variants each play. A {@code Holder} on 1.20+,
     * a plain {@code SoundEvent} on 1.19.2.
     */
    public static SoundEvent caveAmbience() {
        return net.minecraft.sounds.SoundEvents.AMBIENT_CAVE.value();
    }

    public static void playSound(
            ServerLevel serverLevel,
            BlockPos pos,
            SoundEvent sound,
            SoundSource source
    ) {
        float volume = 0.5f;
        float pitchUpOrDown = 1.0F + (serverLevel.random.nextFloat() - serverLevel.random.nextFloat()) * 0.4F;
        serverLevel.playSound(
                null,
                pos,
                sound,
                source,
                volume,
                pitchUpOrDown
        );
    }

    public static MutableComponent translatable(String key) {
        return Component.translatable(key);
    }

    public static MutableComponent translatable(
            String key,
            Object... args
    ) {
        return Component.translatable(key, args);
    }

    /**
     * A translatable that renders {@code fallback} (a format pattern taking the same
     * {@code args}) when {@code key} is missing from the client's lang file, instead of the
     * raw key. Use for status tooltips, where any unauthored category × status pair would
     * otherwise surface as the raw i18n key to the player.
     */
    public static MutableComponent translatableWithFallback(
            String key,
            String fallback,
            Object... args
    ) {
        return Component.translatableWithFallback(key, fallback, args);
    }

    public static Component translatableStyled(
            String s,
            Style style,
            Object... args
    ) {
        MutableComponent v = translatable(s, args);
        v.setStyle(style);
        return v;
    }

    public static Component literal(String x) {
        return Component.literal(x);
    }

    public static <X> ImmutableList<X> shuffle(
            ImmutableCollection<X> c,
            ServerLevel serverLevel
    ) {
        return shuffle(c.iterator(), serverLevel);
    }

    public static <X> ImmutableList<X> shuffle(
            Iterator<X> iterator,
            @Nullable ServerLevel serverLevel
    ) {

        ArrayList<X> list = new ArrayList<>();
        iterator.forEachRemaining(list::add);
        int size = list.size();
        for (int i = size; i > 1; --i) {
            Collections.swap(list, i - 1, getRandomInt(serverLevel, i));
        }
        return ImmutableList.copyOf(list);
    }

    public static int nextRandomInt(
            @Nullable ServerLevel server,
            int i
    ) {
        return server.getRandom().nextInt(i);
    }

    public static Direction getRandomHorizontal(ServerLevel serverLevel) {
        return Direction.Plane.HORIZONTAL.getRandomDirection(serverLevel.getRandom());
    }

    public static void setCutoutRenderType(Block block) {
        // Render layer is set via model JSON files
    }

    public static CompoundTag getBlockStoredTagData(TownFlagBlockEntity e) {
        return e.getPersistentData();
    }

    public static void writeIngredient(net.minecraft.network.FriendlyByteBuf buf, net.minecraft.world.item.crafting.Ingredient i) {
        net.minecraft.world.item.crafting.Ingredient.CONTENTS_STREAM_CODEC.encode(
                (net.minecraft.network.RegistryFriendlyByteBuf) buf, i);
    }

    public static net.minecraft.world.item.crafting.Ingredient readIngredient(net.minecraft.network.FriendlyByteBuf buf) {
        return net.minecraft.world.item.crafting.Ingredient.CONTENTS_STREAM_CODEC.decode(
                (net.minecraft.network.RegistryFriendlyByteBuf) buf);
    }

    /**
     * An item stack on the wire (empty allowed). 1.20.5's item codec needs the registry-aware
     * buffer the network stack hands every message; 1.19.2 uses the buffer's own read/write.
     */
    public static void writeItem(FriendlyByteBuf buf, ItemStack stack) {
        ItemStack.OPTIONAL_STREAM_CODEC.encode((net.minecraft.network.RegistryFriendlyByteBuf) buf, stack);
    }

    public static ItemStack readItem(FriendlyByteBuf buf) {
        return ItemStack.OPTIONAL_STREAM_CODEC.decode((net.minecraft.network.RegistryFriendlyByteBuf) buf);
    }

    /** An ingredient as JSON (1.20.5: the non-empty codec; 1.19.2: Ingredient#toJson). */
    public static com.google.gson.JsonElement ingredientToJson(net.minecraft.world.item.crafting.Ingredient i) {
        return net.minecraft.world.item.crafting.Ingredient.CODEC_NONEMPTY
                .encode(i, com.mojang.serialization.JsonOps.INSTANCE, com.mojang.serialization.JsonOps.INSTANCE.empty())
                .getOrThrow();
    }

    public static net.minecraft.world.item.crafting.Ingredient ingredientFromJson(com.google.gson.JsonElement json) {
        return net.minecraft.world.item.crafting.Ingredient.CODEC_NONEMPTY
                .parse(com.mojang.serialization.JsonOps.INSTANCE, json)
                .getOrThrow();
    }

    /** An item stack as NBT; 1.20.5 needs the level's registries (1.19.2 doesn't). */
    public static CompoundTag saveItem(net.minecraft.world.level.Level level, ItemStack stack) {
        return (CompoundTag) stack.save(level.registryAccess(), new CompoundTag());
    }

    public static ItemStack loadItem(net.minecraft.world.level.Level level, CompoundTag tag) {
        return ItemStack.parseOptional(level.registryAccess(), tag);
    }

    /**
     * The vanilla character skins a townie may wear. 1.20.5 added seven alongside Steve and Alex,
     * under {@code textures/entity/player/slim/}; the townie model is {@code PLAYER_SLIM}, so the
     * slim variants are the matching textures. (1.19.2 has only Steve and Alex.)
     */
    public static java.util.List<ResourceLocation> vanillaCharacterSkins() {
        return ImmutableList.of(
                new ResourceLocation("textures/entity/player/slim/alex.png"),
                new ResourceLocation("textures/entity/player/slim/ari.png"),
                new ResourceLocation("textures/entity/player/slim/efe.png"),
                new ResourceLocation("textures/entity/player/slim/kai.png"),
                new ResourceLocation("textures/entity/player/slim/makena.png"),
                new ResourceLocation("textures/entity/player/slim/noor.png"),
                new ResourceLocation("textures/entity/player/slim/steve.png"),
                new ResourceLocation("textures/entity/player/slim/sunny.png"),
                new ResourceLocation("textures/entity/player/slim/zuri.png")
        );
    }

    public static void openScreen(
            ServerPlayer sender,
            MenuProvider menuProvider,
            Consumer<FriendlyByteBuf> writer) {
        sender.openMenu(menuProvider, buf -> {
            if (writer != null) {
                writer.accept(buf);
            }
        });
    }

    public static void enqueueOrLog(Runnable staticInitialize) {
        ca.bradj.questown.loader.Loader.enqueueWork(staticInitialize);
    }

    public static <X> Supplier<X> configGet(ca.bradj.questown.loader.ConfigValue<X> cfg) {
        return cfg;
    }

    public static TownPosition townPos(
            BlockPos flagPos,
            BlockPos blockPos
    ) {
        return new TownPosition(blockPos.getX(), blockPos.getZ(), blockPos.getY() - flagPos.getY());
    }

    public static boolean insertInNextOpenSlot(
            ca.bradj.questown.loader.ItemHandler iItemHandler,
            ItemStack inserted,
            int targetSize
    ) {
        if (!inserted.has(net.minecraft.core.component.DataComponents.CUSTOM_DATA)) {
            for (int i = 0; i < iItemHandler.getSlots(); i++) {
                ItemStack stackInSlot = iItemHandler.getStackInSlot(i);
                if (!stackInSlot.has(net.minecraft.core.component.DataComponents.CUSTOM_DATA) && ItemStack.isSameItem(stackInSlot, inserted)) {
                    if (stackInSlot.getCount() < targetSize) {
                        iItemHandler.insertItem(i, inserted, false);
                        return true;
                    }
                }
            }
        }
        for (int i = 0; i < iItemHandler.getSlots(); i++) {
            ItemStack stackInSlot = iItemHandler.getStackInSlot(i);
            if (stackInSlot.isEmpty()) {
                iItemHandler.insertItem(i, inserted, false);
                return true;
            }
        }
        return false;
    }

    /** A component as one tooltip line (for multi-line {@code showTooltip}). */
    public static FormattedCharSequence visualOrder(Component c) {
        return c.getVisualOrderText();
    }

    /** {@code text} cut to fit {@code width} pixels, ending in "…" when it was cut. */
    public static String fitToWidth(
            Font font,
            String text,
            int width
    ) {
        if (font.width(text) <= width) {
            return text;
        }
        String ellipsis = "…";
        return font.plainSubstrByWidth(text, Math.max(0, width - font.width(ellipsis))) + ellipsis;
    }

    public static void drawDarkText(
            Font font,
            GuiGraphics gui,
            Component translatable,
            int x,
            int y
    ) {
        gui.drawString(font, translatable, x, y, 0xFF404040, false);
    }

    public static void drawDarkText(
            Font font,
            GuiGraphics gui,
            FormattedCharSequence translatable,
            int x,
            int y
    ) {
        gui.drawString(font, translatable, x, y, 0xFF404040, false);
    }

    public static void drawDarkText(
            Font font,
            GuiGraphics gui,
            String translatable,
            int x,
            int y
    ) {
        gui.drawString(font, translatable, x, y, 0xFF404040, false);
    }
    public static void drawLightText(
            Font font,
            GuiGraphics gui,
            String translatable,
            int x,
            int y
    ) {
        gui.drawString(font, translatable, x, y, 0xFFFFFFFF, true);
    }

    public static Component getItemName(ResourceLocation wantedId) {
        net.minecraft.world.item.Item it = ca.bradj.questown.loader.Loader.itemFromId(wantedId);
        return it == null ? net.minecraft.network.chat.Component.empty() : getItemName(it);
    }

    public static Component getItemName(Item item) {
        return item.getName(item.getDefaultInstance());
    }

    /**
     * The display-name component of a specific stack (honours any custom name in
     * its NBT). Use this when an item must be passed as a {@code Component}
     * argument to a {@code Component.translatable} / {@link #translatable} call
     * - a raw {@link ItemStack} is rejected by 1.20.6's
     * {@code TranslatableContents} (Component/Number/Boolean/String only) and
     * throws {@code IllegalArgumentException}.
     */
    public static Component getItemName(ItemStack stack) {
        return stack.getDisplayName();
    }

    /**
     * The plain name of a stack, as an item tooltip shows it. {@link #getItemName(ItemStack)} is
     * the chat form, wrapped in square brackets.
     */
    public static Component getHoverName(ItemStack stack) {
        return stack.getHoverName();
    }

    public static ResourceLocation getItemId(Item item) {
        return ca.bradj.questown.loader.Loader.itemIdOf(item);
    }

    public static ResourceLocation getItemId(Block block) {
        return ca.bradj.questown.loader.Loader.blockIdOf(block);
    }

    public static boolean getRandomBool(@Nullable ServerLevel serverLevel) {
        return serverLevel.getRandom().nextBoolean();
    }

    public static int getRandomInt(
            ServerLevel serverLevel,
            int size
    ) {
        return serverLevel.getRandom().nextInt(size);
    }

    public static void sendMessage(
            ServerPlayer sender,
            Component message
    ) {
        sender.sendSystemMessage(message);
    }

    /**
     * @return The number of vertical pixels used up when drawing the text
     */
    public static int drawDarkTextWrap(
            Font font,
            GuiGraphics gui,
            Coordinate topLeft,
            int textWidth,
            Component translatable
    ) {
        return drawTextWrap(font, gui, topLeft, textWidth, translatable, 0xFF404040, false);
    }

    public static int drawRedTextWrap(
            Font font,
            GuiGraphics gui,
            Coordinate topLeft,
            int textWidth,
            Component translatable
    ) {
        return drawTextWrap(font, gui, topLeft, textWidth, translatable, 0xFFFF0000, true);
    }

    /**
     * Draw wrapped {@code translatable} in {@code color} and record the extent when the gui-lint
     * {@link ca.bradj.questown.gui.GuiLayoutCapture} is active. Dark and red text both delegate
     * here so the linter captures either.
     *
     * @return The number of vertical pixels used up when drawing the text
     */
    public static int drawTextWrap(
            Font font,
            GuiGraphics gui,
            Coordinate topLeft,
            int textWidth,
            Component translatable,
            int color,
            boolean dropShadow
    ) {
        boolean capture = ca.bradj.questown.gui.GuiLayoutCapture.isActive();
        int out = 0;
        int maxLineWidth = 0;
        for (FormattedCharSequence line : font.split(translatable, textWidth)) {
            gui.drawString(font, line, topLeft.x(), topLeft.y() + out, color, dropShadow);
            if (capture) {
                maxLineWidth = Math.max(maxLineWidth, font.width(line));
            }
            out += (int) (font.lineHeight * 1.5);
        }
        if (capture) {
            ca.bradj.questown.gui.GuiLayoutCapture.recordText(
                    topLeft.x(), topLeft.y(), maxLineWidth, out, translatable.getString());
        }
        return out;
    }

    public static double randomTriangle(
            double v,
            double v1
    ) {
        return RANDOM.triangle(v, v1);
    }

    public static Vec3 relative(
            Vec3 start,
            Direction dir,
            double amount
    ) {
        return start.relative(dir, amount);
    }

    public static float nextFloat(
            float v,
            float v1
    ) {
        return RANDOM.nextFloat() * (v1 - v) + v;
    }

    public static ItemRenderer itemRenderer() {
        return Minecraft.getInstance().getItemRenderer();
    }

    /**
     * An item tag (a <code>#tag</code> id) by namespace-qualified location. Route the
     * <code>net.minecraft.core.registries</code> access through the seam so leaves name
     * only this helper (a name like {@code itemTag} dodges the gate's package marker).
     */
    public static TagKey<Item> itemTag(ResourceLocation tag) {
        return TagKey.create(Registries.ITEM, tag);
    }

    /** Per-item NBT mutation (1.19.2: item NBT; 1.20.4+: the custom-data component). */
    public static void updateItemDataTag(
            net.minecraft.world.item.ItemStack stack,
            java.util.function.Consumer<net.minecraft.nbt.CompoundTag> mutate
    ) {
        net.minecraft.world.item.component.CustomData.update(
                net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                stack,
                mutate
        );
    }

    public static void renderItemAndDecorations(
            GuiGraphics gui,
            ItemStack stack,
            int x,
            int y
    ) {
        gui.renderItem(stack, x, y);
        gui.renderItemDecorations(Minecraft.getInstance().font, stack, x, y);
    }

    /** Static-render an item in the GUI display context. */
    public static void renderItemStaticGui(
            ItemRenderer renderer,
            ItemStack stack,
            int lightLevel,
            com.mojang.blaze3d.vertex.PoseStack poseStack,
            net.minecraft.client.renderer.MultiBufferSource buffer,
            net.minecraft.world.level.Level level,
            int shade
    ) {
        renderer.renderStatic(stack, ItemDisplayContext.GUI, lightLevel,
                net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY,
                poseStack, buffer, level, shade);
    }

    public static List<FormattedCharSequence> splitText(
            Font font,
            Component text,
            int width
    ) {
        return font.split(text, width);
    }

    public static net.minecraft.client.gui.components.Button button(
            Component text,
            net.minecraft.client.gui.components.Button.OnPress onPress,
            int x,
            int y,
            int width,
            int height
    ) {
        return net.minecraft.client.gui.components.Button.builder(text, onPress).bounds(x, y, width, height).build();
    }

    public static FormattedCharSequence componentText(Component component) {
        return component.getVisualOrderText();
    }

    public static void setShaderColor(float r, float g, float b, float a) {
        com.mojang.blaze3d.systems.RenderSystem.setShaderColor(r, g, b, a);
    }

    public static void resetShaderColor() {
        com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
    }

    /**
     * The block-property form-owner for a strangler colour token: builds the {@code Properties} a
     * block's map colour attaches to, so a block ctor names {@link CompatBlockColor} (not the strangler-
     * marked {@code MapColor}) and the version-bound {@code Properties.of().mapColor(...)} form stays
     * in the seam. See {@link CompatBlockColor}.
     */
    public static BlockBehaviour.Properties blockProps(CompatBlockColor color) {
        return BlockBehaviour.Properties.of().mapColor(color.mapColor());
    }

    /**
     * The 1.19.2 "arbitrary item NBT" surface (block/entity data attached to an {@link ItemStack}).
     * The component split put it in the {@code DataComponents.CUSTOM_DATA} component. The version-bound
     * read lives here so a leaf names neither {@code DataComponents} nor the strangler-marked {@code
     * CustomData} type (the two substrings of it) - the same name-collision trap as the {@code MapColor}
     * one: note the helpers are named {@code itemDataTag}/{@code setItemDataTag}, not *CustomData*.
     */
    public static CompoundTag itemDataTag(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
    }

    /**
     * The inverse of {@link #itemDataTag(ItemStack)}: writes a tag back as the item's {@code CUSTOM_DATA}
     * component.
     */
    public static void setItemDataTag(ItemStack stack, CompoundTag t) {
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(t));
    }

    private static ToolAction forgeToolAction(QTToolAction toolAction) {
        return switch (toolAction) {
            case HOE_TILL -> ToolActions.HOE_TILL;
            case AXE_STRIP -> ToolActions.AXE_STRIP;
            case AXE_SCRAPE -> ToolActions.AXE_SCRAPE;
            case SHOVEL_FLATTEN -> ToolActions.SHOVEL_FLATTEN;
        };
    }

    /**
     * The tool-modified state a block takes under a neutral {@code QTToolAction}, or {@code null}
     * when it is not tool-transformable.
     */
    public static net.minecraft.world.level.block.state.BlockState toolModifiedState(
            net.minecraft.world.level.block.state.BlockState state,
            net.minecraft.world.item.context.UseOnContext context,
            QTToolAction toolAction
    ) {
        return state.getToolModifiedState(context, forgeToolAction(toolAction), false);
    }

    /* Registry-access seam (G4). Each helper owns one 1.20 Registries constant so the
     * init/holder leaves pass only a neutral id and never name the marked package. */

    /** The item registry keyed by name - the 1.20 lookup behind the item-id read/write. */
    public static Item itemFromId(net.minecraft.resources.ResourceLocation id) {
        return net.minecraft.core.registries.BuiltInRegistries.ITEM.get(id);
    }

    /** The item registry as a stream (the pool generator iterates it; the golden iterates
     * the same 1.19.2 item registry inline). */
    public static java.util.stream.Stream<Item> itemRegistryStream() {
        return net.minecraft.core.registries.BuiltInRegistries.ITEM.stream();
    }

    /** The SCHEDULE registry key, for the HolderRegistry the visitor schedule registers onto. */
    public static net.minecraft.resources.ResourceKey<net.minecraft.core.Registry<net.minecraft.world.entity.schedule.Schedule>> scheduleRegistry() {
        return Registries.SCHEDULE;
    }

    /** The BLOCK_ENTITY_TYPE registry key, for the block-entity-type HolderRegistries. */
    public static net.minecraft.resources.ResourceKey<net.minecraft.core.Registry<net.minecraft.world.level.block.entity.BlockEntityType<?>>> blockEntityTypeRegistry() {
        return Registries.BLOCK_ENTITY_TYPE;
    }

    /** The COMMAND_ARGUMENT_TYPE registry key, for the command-argument HolderRegistry. */
    public static net.minecraft.resources.ResourceKey<net.minecraft.core.Registry<net.minecraft.commands.synchronization.ArgumentTypeInfo<?, ?>>> commandArgumentTypeRegistry() {
        return Registries.COMMAND_ARGUMENT_TYPE;
    }

    /** A block tag key (1.20 TagKey over a block registry) so the wall-detection glass
     * tags never name the Registries constant. */
    public static TagKey<Block> blockTag(net.minecraft.resources.ResourceLocation id) {
        return TagKey.create(Registries.BLOCK, id);
    }

    /**
     * The loot table with the given id.
     */
    public static net.minecraft.world.level.storage.loot.LootTable lootTable(
            ServerLevel level,
            ResourceLocation id
    ) {
        return level.getServer()
                .reloadableRegistries()
                .getLootTable(net.minecraft.resources.ResourceKey.create(Registries.LOOT_TABLE, id));
    }

    /** Whether a loot table with the given id exists on the server (loot-table registry keys). */
    public static boolean hasLootTable(
            ServerLevel level,
            ResourceLocation id
    ) {
        return level.getServer()
                .reloadableRegistries()
                .getKeys(Registries.LOOT_TABLE)
                .contains(id);
    }

    /**
     * A fluent block-properties chain that carries the 1.20 map-colour API (and the 1.19.2
     * {@code Properties#color(MaterialColor)} equivalent when porting). Everything else is a
     * pass-through, so a leaf keeps the vanilla chain shape - only the map colour goes through
     * the {@link MapC} alias. The 1.20-only {@code MapColor} type never appears in a leaf.
     */
    public static final class P {

        private final net.minecraft.world.level.block.state.BlockBehaviour.Properties props;

        private P(net.minecraft.world.level.block.state.BlockBehaviour.Properties props) {
            this.props = props;
        }

        public static P of() {
            return new P(net.minecraft.world.level.block.state.BlockBehaviour.Properties.of());
        }

        /** The block's static map colour (1.20.x {@code Properties#mapColor(MapColor)}). */
        public P mapColor(MapC c) {
            props.mapColor(c.vanilla());
            return this;
        }

        /** A per-state map colour. */
        public P mapColor(
                java.util.function.Function<
                    net.minecraft.world.level.block.state.BlockState,
                    net.minecraft.world.level.material.MapColor
                > f
        ) {
            props.mapColor(f);
            return this;
        }

        public P strength(float destroyTime, float explosionResistance) {
            props.strength(destroyTime, explosionResistance);
            return this;
        }

        public P strength(float destroyTime) {
            props.strength(destroyTime);
            return this;
        }

        public P noOcclusion() {
            props.noOcclusion();
            return this;
        }

        public P noCollission() {
            props.noCollission();
            return this;
        }

        public P sound(net.minecraft.world.level.block.SoundType s) {
            props.sound(s);
            return this;
        }

        public P lightLevel(
                java.util.function.ToIntFunction<net.minecraft.world.level.block.state.BlockState> f
        ) {
            props.lightLevel(f);
            return this;
        }

        public net.minecraft.world.level.block.state.BlockBehaviour.Properties impl() {
            return props;
        }
    }

    /**
     * A player head whose face resolves to the given UUID (empty texture, the profile's UUID,
     * no authlib properties). The 1.20.4+ data-component surface behind this --
     * {@link net.minecraft.core.component.DataComponents#PROFILE}, a
     * {@link net.minecraft.world.item.component.ResolvableProfile}, and {@code ItemStack.set} -- is
     * version-bound and stays here so no leaf outside the seam names it (NBT in 1.19.2, components
     * from 1.20.4 up).
     */
    public static ItemStack resolvablePlayerHead(java.util.UUID uuid) {
        ItemStack head = new ItemStack(net.minecraft.world.item.Items.PLAYER_HEAD);
        head.set(
                net.minecraft.core.component.DataComponents.PROFILE,
                new net.minecraft.world.item.component.ResolvableProfile(
                        java.util.Optional.empty(),
                        java.util.Optional.of(uuid),
                        new com.mojang.authlib.properties.PropertyMap()
                )
        );
        return head;
    }

    /**
     * The 1.20.3+ tick-rate lever - what {@code /tick rate <tps>} does on a live
     * server. The autotest runner applies this on boot so tick-budgeted
     * scenarios (the scenario clock is ticks, never wall clock) deliver in a
     * fraction of the wall time; 1.19.2 has a fixed 20 t/s with no lever.
     */
    public static void setServerTickRate(MinecraftServer server, float tps) {
        server.tickRateManager().setTickRate(tps);
    }

    /**
     * Stops the spawn chunks keeping the area around the world spawn loaded (the autotest origin sits
     * in them), so village/day1's flag chunk can unload. Sets the {@code spawnChunkRadius} gamerule to 0;
     * {@code elsewhere} is where 1.20.1, which has no such gamerule, moves the world spawn instead.
     */
    public static void releaseSpawnChunks(MinecraftServer server, ServerLevel level, BlockPos elsewhere) {
        level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_SPAWN_CHUNK_RADIUS).set(0, server);
    }

    /**
     * The room recipe registered under {@code id}, or null. 1.20.2+ wraps recipes in a
     * {@code RecipeHolder} (1.19.2: the recipe carries its own id).
     */
    public static @Nullable ca.bradj.roomrecipes.recipes.RoomRecipe findRoomRecipe(ServerLevel level, ResourceLocation id) {
        return level.getRecipeManager().getAllRecipesFor(ca.bradj.roomrecipes.recipes.RecipesInit.ROOM).stream()
                .filter(h -> h.id().equals(id))
                .map(net.minecraft.world.item.crafting.RecipeHolder::value)
                .findFirst()
                .orElse(null);
    }

    /**
     * Whether the player has completed the advancement. 1.20.2+ looks advancements up as
     * an {@code AdvancementHolder} (1.19.2: {@code getAdvancement} returns the
     * {@code Advancement}). An unknown id reads as not completed.
     */
    public static boolean hasCompletedAdvancement(ServerPlayer player, ResourceLocation advancementId) {
        net.minecraft.advancements.AdvancementHolder adv = player.getServer().getAdvancements().get(advancementId);
        if (adv == null) {
            return false;
        }
        return player.getAdvancements().getOrStartProgress(adv).isDone();
    }
}

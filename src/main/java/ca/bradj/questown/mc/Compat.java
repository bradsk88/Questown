package ca.bradj.questown.mc;

import ca.bradj.questown.core.Coordinate;
import ca.bradj.questown.town.entity.TownFlagBlockEntity;
import ca.bradj.questown.town.rooms.TownPosition;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.RandomSource;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Compat executes the "strangler" pattern. Putting all code that changes
 * between minecraft versions in a centralized location to minimize the
 * effort required for forward and back-porting this mod between versions.
 */
public class Compat {
    public static final RandomSource RANDOM = RandomSource.create();

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

    public static Component translatable(String key) {
        return Component.translatable(key);
    }

    public static MutableComponent translatable(
            String key,
            Object... args
    ) {
        return Component.translatable(key, args);
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

    public static void openScreen(
            ServerPlayer sender,
            MenuProvider menuProvider,
            Consumer<net.minecraft.network.RegistryFriendlyByteBuf> writer) {
        sender.openMenu(menuProvider, writer == null ? buf -> {} : writer);
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

    public static void renderItemAndDecorations(
            GuiGraphics gui,
            ItemStack stack,
            int x,
            int y
    ) {
        gui.renderItem(stack, x, y);
        gui.renderItemDecorations(Minecraft.getInstance().font, stack, x, y);
    }

    public static List<FormattedCharSequence> splitText(
            Font font,
            Component text,
            int width
    ) {
        return font.split(text, width);
    }

    /**
     * The 1.20.6 button construction (a protected ctor, built via the builder). A leaf calls this
     * with the version-bound {@code net.minecraft.client.gui.components.Button} type, so it appears
     * nowhere outside the seam.
     */
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

    /**
     * The 1.20.6 way to turn a {@link Component} into its display order text (1.19.2: a {@code
     * FormattedCharSequence} cast). The version-bound {@code getVisualOrderText()} call stays here.
     */
    public static FormattedCharSequence componentText(Component component) {
        return component.getVisualOrderText();
    }

    /**
     * The vanilla per-frame state setters the SAFE-tier card screens used to name directly
     * ({@code RenderSystem.setShaderColor(r, g, b, a)} before/after a tinted card). The version-bound
     * static stays here; leaves call this.
     */
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
}

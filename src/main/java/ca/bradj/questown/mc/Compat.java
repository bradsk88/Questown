package ca.bradj.questown.mc;

import ca.bradj.questown.QT;
import ca.bradj.questown.core.Coordinate;
import ca.bradj.questown.core.init.CommandsInit;
import ca.bradj.questown.town.entity.TownFlagBlockEntity;
import ca.bradj.questown.town.rooms.TownPosition;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.Font;
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
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkHooks;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.IForgeRegistry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class Compat {
    public static final RandomSource RANDOM = RandomSource.create();
    public static final IForgeRegistry<EntityType<?>> ENTITY_TYPES = ForgeRegistries.ENTITY_TYPES;
    public static final @NotNull Capability<IItemHandler> ITEM_HANDLER = ForgeCapabilities.ITEM_HANDLER;

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

    public static int nextInt(
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

    public static <MSG> SimpleChannel.MessageBuilder<MSG> withConsumer(
            SimpleChannel.MessageBuilder<MSG> decoder,
            BiConsumer<MSG, Supplier<NetworkEvent.Context>> consumer
    ) {
        return decoder.consumerNetworkThread(consumer);
    }

    public static CompoundTag getBlockStoredTagData(TownFlagBlockEntity e) {
        return e.getPersistentData();
    }

    public static void openScreen(
            ServerPlayer sender,
            MenuProvider menuProvider,
            Consumer<FriendlyByteBuf> consumer
    ) {
        NetworkHooks.openScreen(sender, menuProvider, consumer);
    }

    public static DeferredRegister<MenuType<?>> CreateMenuRegister(String modid) {
        return DeferredRegister.create(ForgeRegistries.MENU_TYPES, modid);
    }

    public static void enqueueOrLog(
            FMLCommonSetupEvent event,
            Runnable staticInitialize
    ) {
        event.enqueueWork(staticInitialize).exceptionally(
                ex -> {
                    QT.INIT_LOGGER.error("Enqueued work failed", ex);
                    return null;
                }
        );
    }

    public static <X> Supplier<X> configGet(ForgeConfigSpec.ConfigValue<X> cfg) {
        return cfg::get;
    }

    public static TownPosition townPos(
            BlockPos flagPos,
            BlockPos blockPos
    ) {
        return new TownPosition(blockPos.getX(), blockPos.getZ(), blockPos.getY() - flagPos.getY());
    }

    public static boolean insertInNextOpenSlot(
            IItemHandler iItemHandler,
            ItemStack inserted,
            int targetSize
    ) {
        if (inserted.getOrCreateTag().isEmpty()) {
            for (int i = 0; i < iItemHandler.getSlots(); i++) {
                ItemStack stackInSlot = iItemHandler.getStackInSlot(i);
                if (stackInSlot.getOrCreateTag().isEmpty() && stackInSlot.sameItem(inserted)) {
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
            PoseStack stack,
            Component translatable,
            int x,
            int y
    ) {
        font.draw(stack, translatable, x, y, 0x00000000);
    }

    public static void drawDarkText(
            Font font,
            PoseStack stack,
            FormattedCharSequence translatable,
            int x,
            int y
    ) {
        font.draw(stack, translatable, x, y, 0x00000000);
    }

    public static void drawLightText(
            Font font,
            PoseStack stack,
            String translatable,
            int x,
            int y
    ) {
        font.drawShadow(stack, translatable, x, y, 0xFFFFFFFF);
    }

    public static Component getItemName(ResourceLocation wantedId) {
        return getItemName(ForgeRegistries.ITEMS.getValue(wantedId));
    }

    public static Component getItemName(Item item) {
        return item.getName(item.getDefaultInstance());
    }

    public static ResourceLocation getItemId(Item item) {
        return ForgeRegistries.ITEMS.getKey(item);
    }

    public static ResourceLocation getItemId(Block block) {
        return ForgeRegistries.BLOCKS.getKey(block);
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

    public static void initCommands(IEventBus bus) {
        CommandsInit.register(bus);
    }

    /**
     * @return The number of vertical pixels used up when drawing the text
     */
    public static int drawDarkTextWrap(
            Font font,
            PoseStack poseStack,
            Coordinate topLeft,
            int textWidth,
            Component translatable
    ) {
        int out = 0;
        for (FormattedCharSequence line : font.split(translatable, textWidth)) {
            drawDarkText(font, poseStack, line, topLeft.x(), topLeft.y() + out);
            out += (int) (font.lineHeight * 1.5);
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

    public static List<FormattedCharSequence> splitText(
            Font font,
            Component text,
            int width
    ) {
        return font.split(text, width);
    }
}

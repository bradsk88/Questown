package ca.bradj.questown.mc;

import ca.bradj.questown.QT;
import ca.bradj.questown.jobs.WorkSpot;
import ca.bradj.questown.town.TownFlagBlockEntity;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.event.lifecycle.ParallelDispatchEvent;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkHooks;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class Compat {
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
            SoundEvent sound, SoundSource source
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
        return new TranslatableComponent(key);
    }

    public static Component translatable(String key, Object... args) {
        return new TranslatableComponent(key, args);
    }

    public static Component literal(String x) {
        return new TextComponent(x);
    }

    public static <X> ArrayList<X> shuffle(Collection<X> c, ServerLevel serverLevel) {
        ArrayList<X> list = new ArrayList<>(c);
        int size = list.size();
        for (int i = size; i > 1; --i) {
            Collections.swap(list, i - 1, serverLevel.getRandom().nextInt(i));
        }
        return list;
    }

    public static int nextInt(@Nullable ServerLevel server, int i) {
        return server.getRandom().nextInt(i);
    }

    public static Direction getRandomHorizontal(ServerLevel serverLevel) {
        return Direction.Plane.HORIZONTAL.getRandomDirection(serverLevel.getRandom());
    }

    public static void setCutoutRenderType(Block block) {
        ItemBlockRenderTypes.setRenderLayer(block, RenderType.cutout());
    }

    public static <MSG> SimpleChannel.MessageBuilder<MSG> withConsumer(
            SimpleChannel.MessageBuilder<MSG> decoder,
            BiConsumer<MSG, Supplier<NetworkEvent.Context>> consumer
    ) {
        return decoder.consumer(consumer);
    }

    public static CompoundTag getBlockStoredTagData(TownFlagBlockEntity e) {
        return e.getTileData();
    }

    public static void openScreen(ServerPlayer sender, MenuProvider menuProvider, Consumer<FriendlyByteBuf> consumer) {
        NetworkHooks.openGui(sender, menuProvider, consumer);
    }

    public static DeferredRegister<MenuType<?>> CreateMenuRegister(String modid) {
        return DeferredRegister.create(ForgeRegistries.CONTAINERS, modid);
    }

    public static void enqueueOrLog(ParallelDispatchEvent event, Runnable staticInitialize) {
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
}

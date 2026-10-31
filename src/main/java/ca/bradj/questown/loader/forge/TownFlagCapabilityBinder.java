package ca.bradj.questown.loader.forge;

import ca.bradj.questown.Questown;
import ca.bradj.questown.town.entity.TownFlagBlockEntity;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.items.IItemHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Exposes the flag's Block-of-Progress inventory as Forge's item-handler capability (the 1.20.6
 * branch registers it through NeoForge's RegisterCapabilitiesEvent instead).
 */
@EventBusSubscriber(modid = Questown.MODID, bus = EventBusSubscriber.Bus.FORGE)
public final class TownFlagCapabilityBinder {

    private static final ResourceLocation ID = new ResourceLocation(Questown.MODID, "flag_bop_handler");

    private TownFlagCapabilityBinder() {
    }

    @SubscribeEvent
    public static void attach(AttachCapabilitiesEvent<BlockEntity> event) {
        if (!(event.getObject() instanceof TownFlagBlockEntity flag)) {
            return;
        }
        LazyOptional<IItemHandler> handler = LazyOptional.of(() -> new TownFlagBOPHandlerBridge(flag.bopItemHandler()));
        event.addCapability(ID, new ICapabilityProvider() {
            @Override
            public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
                return ForgeCapabilities.ITEM_HANDLER.orEmpty(cap, handler);
            }
        });
        event.addListener(handler::invalidate);
    }
}

package ca.bradj.questown.loader.neo;

import ca.bradj.questown.Questown;
import ca.bradj.questown.core.init.TilesInit;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

/**
 * Registers the town flag's BOP deposit handler (neutral
 * {@link TownFlagBlockEntity#bopItemHandler()}) as a NeoForge item-handler capability on the
 * flag's block-entity type. Without this, NeoForge 1.20.6's block-entity capability lookup
 * (which replaced 1.19.2's {@code BlockEntity.getCapability} + {@code LazyOptional}) resolves
 * nothing for the flag, so the facade retrieval ({@code Loader.handlerOf(BlockEntity)}) —
 * the leaver's deposit path and the flag's container-change watch — silently degrades: the
 * live server log carries "[questown] no item-handler capability for TownFlagBlockEntity".
 *
 * <p>Deposit only: the provider hands out {@link TownFlagBOPHandlerBridge} (no
 * {@code IItemHandlerModifiable}); extraction is unsupported by the flag by design.
 */
@EventBusSubscriber(modid = Questown.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class TownFlagCapabilityBinder {

    private TownFlagCapabilityBinder() {
    }

    @SubscribeEvent
    public static void register(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                TilesInit.TOWN_FLAG.get(),
                (flag, direction) -> new TownFlagBOPHandlerBridge(flag.bopItemHandler()));
    }
}

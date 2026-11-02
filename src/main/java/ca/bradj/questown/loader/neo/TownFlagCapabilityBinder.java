package ca.bradj.questown.loader.neo;

import ca.bradj.questown.Questown;
import ca.bradj.questown.core.init.TilesInit;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

/** Registers the flag's Block of Progress handler as an item-handler capability, so villagers can deposit into the flag. */
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

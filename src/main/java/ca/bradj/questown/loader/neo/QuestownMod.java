package ca.bradj.questown.loader.neo;

import ca.bradj.questown.Questown;
import ca.bradj.questown.loader.Loader;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.javafmlmod.FMLJavaModLoadingContext;

/**
 * NeoForge 1.20.6 {@code @Mod} entry.
 *
 * <p>Installs the {@link NeoLoader} as the neutral {@link Loader}, registers the config specs,
 * defines the registries, and binds the mod-bus setup/client events onto the NeoForge event bus.
 * The heavy per-loader work (network, capabilities) degrades to log-and-skip until its slice lands.
 */
@Mod(Questown.MODID)
public class QuestownMod {

    public QuestownMod() {
        NeoLoader loader = new NeoLoader();
        Loader.init(loader);

        Questown.registerConfigs();
        Loader.defineRegistries();

        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        bus.addListener(FMLCommonSetupEvent.class, e -> e.enqueueWork(Questown::onCommonSetup));
        if (loader.isClient()) {
            bus.addListener(FMLClientSetupEvent.class, e -> e.enqueueWork(Questown::onClientSetup));
        }

        // Game (common) bus handlers. The item-interaction binder is the first; the dev-only bridges
        // (AgentMessageBridge, ServerTickProfiler) join the same pattern in the events slice.
        loader.registerCommonHandlers(new ItemsInteract());
    }
}

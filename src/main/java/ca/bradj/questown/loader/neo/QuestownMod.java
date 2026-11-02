package ca.bradj.questown.loader.neo;

import ca.bradj.questown.Questown;
import ca.bradj.questown.loader.Loader;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.javafmlmod.FMLJavaModLoadingContext;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** The NeoForge 1.20.6 mod entry: installs the loader, registers the config, the registries and the setup events. */
@Mod(Questown.MODID)
public class QuestownMod {

    public QuestownMod() {
        NeoLoader loader = new NeoLoader();
        Loader.init(loader);

        Questown.registerConfigs();
        Loader.defineRegistries();

        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        bus.addListener(FMLCommonSetupEvent.class, e -> e.enqueueWork(Questown::onCommonSetup));
        // After common setup, so the message registry is filled.
        bus.addListener(RegisterPayloadHandlersEvent.class, loader::onRegisterPayloadHandlers);

        loader.registerCommonHandlers(new ItemsInteract());

        // The trigger registry is fixed early on 1.20.6, so the triggers register on the mod bus.
        NeoAdvancementTriggers.init(bus);
    }
}

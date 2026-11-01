package ca.bradj.questown.loader.forge;

import ca.bradj.questown.Questown;
import ca.bradj.questown.loader.Loader;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

/**
 * The Forge 1.19.2 {@code @Mod} entry: installs the neutral loader seam, then wires the mod bus.
 * Forge 1.19.2 has no client-only {@code @Mod}, so the client island starts from here.
 */
@Mod(Questown.MODID)
public class QuestownMod {

    public QuestownMod() {
        ForgeLoader loader = new ForgeLoader();
        Loader.init(loader);

        Questown.registerConfigs();
        Loader.defineRegistries();

        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        // SimpleChannel messages register as Questown.onCommonSetup fills the neutral registry.
        bus.addListener((FMLCommonSetupEvent e) -> e.enqueueWork(Questown::onCommonSetup));

        loader.registerCommonHandlers(new ItemsInteract());
        ForgeAdvancementTriggers.init(bus);

        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> QuestownClientMod.init(bus));
    }
}

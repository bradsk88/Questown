package ca.bradj.questown.loader.neo;

import ca.bradj.questown.QuestownClient;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.javafmlmod.FMLJavaModLoadingContext;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/**
 * Client-only mod entry ({@code questown.client}, dist=CLIENT in {@code neoforge.mods.toml}).
 *
 * <p>NeoForge skips this mod entirely on a dedicated server, so the client classes it
 * references (screens, renderers, the scroll dispatcher) are never loaded there and the
 * runtime dist cleaner never sees them. The common entry ({@link QuestownMod}) must stay
 * free of client-class references for the same reason.
 */
@Mod(value = "questownclient", dist = Dist.CLIENT)
public class QuestownClientMod {

    public QuestownClientMod() {
        NeoLoaderClient.init();

        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        bus.addListener(FMLClientSetupEvent.class, e -> e.enqueueWork(QuestownClient::onClientSetup));

        // Fired at client init (before client setup), so the captures in registerScreens
        // must be filled before the listener fires: fill them synchronously now, then register.
        QuestownClient.registerScreens();
        bus.addListener(RegisterMenuScreensEvent.class, NeoLoaderClient::onRegisterMenuScreens);
    }
}

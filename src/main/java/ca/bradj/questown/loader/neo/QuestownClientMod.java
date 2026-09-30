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
 *
 * <p>Nothing that can touch {@code Loader.api()} runs in the constructor: the mod entries
 * have no guaranteed construction order, so all loader-seam work is in the
 * {@code FMLClientSetupEvent} queue (always after every mod constructor).
 */
@Mod(value = "questownclient", dist = Dist.CLIENT)
public class QuestownClientMod {

    public QuestownClientMod() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        // The neutral loader seam is installed by the questown mod's constructor; mod
        // constructor order between the two entries is not guaranteed, so nothing that
        // needs Loader.api() runs in this constructor. Island init (client send hook,
        // scroll dispatch) is deferred to client setup. Menu-screen capture happens at
        // RegisterMenuScreensEvent (see NeoLoaderClient) rather than in this queue.
        bus.addListener(FMLClientSetupEvent.class, e -> e.enqueueWork(NeoLoaderClient::init));
        bus.addListener(FMLClientSetupEvent.class, e -> e.enqueueWork(QuestownClient::onClientSetup));
        bus.addListener(RegisterMenuScreensEvent.class, NeoLoaderClient::onRegisterMenuScreens);
        // Port-test hook (active only with -Dquestown.diag=true; see QtDiagClient).
        // ClientChatReceivedEvent is a game-bus event, not a mod-bus event.
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS
                .addListener(net.neoforged.neoforge.client.event.ClientChatReceivedEvent.class,
                        e -> QtDiagClient.onChat(e));
        QtDiagClient.install();
    }
}

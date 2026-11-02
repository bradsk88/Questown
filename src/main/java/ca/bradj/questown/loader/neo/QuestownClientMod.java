package ca.bradj.questown.loader.neo;

import ca.bradj.questown.QuestownClient;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.javafmlmod.FMLJavaModLoadingContext;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/** The client-only mod entry. NeoForge skips it on a dedicated server, so client classes it names never load there. Nothing here may use {@code Loader.api()} in the constructor. */
@Mod(value = "questownclient", dist = Dist.CLIENT)
public class QuestownClientMod {

    public QuestownClientMod() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        // The loader isn't installed yet when this runs, so the client setup waits for the setup queue.
        bus.addListener(FMLClientSetupEvent.class, e -> e.enqueueWork(NeoLoaderClient::init));
        bus.addListener(FMLClientSetupEvent.class, e -> e.enqueueWork(QuestownClient::onClientSetup));
        bus.addListener(RegisterMenuScreensEvent.class, NeoLoaderClient::onRegisterMenuScreens);
        // Screen-capture hook, only with -Dquestown.diag=true.
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS
                .addListener(net.neoforged.neoforge.client.event.ClientChatReceivedEvent.class,
                        e -> QtDiagClient.onChat(e));
        QtDiagClient.install();
    }
}

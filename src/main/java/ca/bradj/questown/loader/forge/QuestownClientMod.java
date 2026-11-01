package ca.bradj.questown.loader.forge;

import ca.bradj.questown.QuestownClient;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/** Client-side wiring, called from {@link QuestownMod} on the client only. */
public final class QuestownClientMod {

    private QuestownClientMod() {
    }

    static void init(IEventBus bus) {
        bus.addListener((FMLClientSetupEvent e) -> e.enqueueWork(() -> {
            QuestownClient.onClientSetup();
            ForgeLoaderClient.init();
        }));
        // Port-test hook (active only with -Dquestown.diag=true; see QtDiagClient).
        MinecraftForge.EVENT_BUS.addListener((ClientChatReceivedEvent e) -> QtDiagClient.onChat(e));
        QtDiagClient.install();
    }
}

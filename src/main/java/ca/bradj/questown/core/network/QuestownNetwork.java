package ca.bradj.questown.core.network;

import ca.bradj.questown.Questown;
import ca.bradj.questown.mc.Compat;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public class QuestownNetwork {

    public static final String NETWORK_VERSION = "0.0.1";

    private static int messageIndex = 0;

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(Questown.MODID, "network"),
            () -> NETWORK_VERSION,
            version -> version.equals(NETWORK_VERSION),
            version -> version.equals(NETWORK_VERSION)
    );

    public static void init() {
        initMessagesToServer();
        initMessagesToClient();
    }

    private static void initMessagesToServer() {
        Compat.withConsumer(
                registerMessage(AddWorkFromUIMessage.class, NetworkDirection.PLAY_TO_SERVER).
                        encoder(AddWorkFromUIMessage::encode).
                        decoder(AddWorkFromUIMessage::decode),
                AddWorkFromUIMessage::handle
        ).add();
        Compat.withConsumer(
                registerMessage(RemoveWorkFromUIMessage.class, NetworkDirection.PLAY_TO_SERVER).
                        encoder(RemoveWorkFromUIMessage::encode).
                        decoder(RemoveWorkFromUIMessage::decode),
                RemoveWorkFromUIMessage::handle
        ).add();
        Compat.withConsumer(
                registerMessage(OpenQuestsMenuMessage.class, NetworkDirection.PLAY_TO_SERVER).
                        encoder(OpenQuestsMenuMessage::encode).
                        decoder(OpenQuestsMenuMessage::decode),
                OpenQuestsMenuMessage::handle
        ).add();
        Compat.withConsumer(
                registerMessage(RemoveQuestFromUIMessage.class, NetworkDirection.PLAY_TO_SERVER).
                        encoder(RemoveQuestFromUIMessage::encode).
                        decoder(RemoveQuestFromUIMessage::decode),
                RemoveQuestFromUIMessage::handle
        ).add();
        Compat.withConsumer(
                registerMessage(OpenVillagerMenuMessage.class, NetworkDirection.PLAY_TO_SERVER).
                        encoder(OpenVillagerMenuMessage::encode).
                        decoder(OpenVillagerMenuMessage::decode),
                OpenVillagerMenuMessage::handle
        ).add();
        Compat.withConsumer(
                registerMessage(OpenFlagMenuMessage.class, NetworkDirection.PLAY_TO_SERVER).
                        encoder(OpenFlagMenuMessage::encode).
                        decoder(OpenFlagMenuMessage::decode),
                OpenFlagMenuMessage::handle
        ).add();
        Compat.withConsumer(
                registerMessage(ChangeVillagerJobMessage.class, NetworkDirection.PLAY_TO_SERVER).
                        encoder(ChangeVillagerJobMessage::encode).
                        decoder(ChangeVillagerJobMessage::decode),
                ChangeVillagerJobMessage::handle
        ).add();
        Compat.withConsumer(
                registerMessage(CreateStockRequestFromUIMessage.class, NetworkDirection.PLAY_TO_SERVER).
                        encoder(CreateStockRequestFromUIMessage::encode).
                        decoder(CreateStockRequestFromUIMessage::decode),
                CreateStockRequestFromUIMessage::handle
        ).add();
        Compat.withConsumer(
                registerMessage(OpenMultiVillagerMenuMessage.class, NetworkDirection.PLAY_TO_SERVER).
                        encoder(OpenMultiVillagerMenuMessage::encode).
                        decoder(OpenMultiVillagerMenuMessage::decode),
                OpenMultiVillagerMenuMessage::handle
        ).add();
    }

    private static void initMessagesToClient() {
        Compat.withConsumer(
                registerMessage(SyncBlockItemMessage.class, NetworkDirection.PLAY_TO_CLIENT).
                        encoder(SyncBlockItemMessage::encode).
                        decoder(SyncBlockItemMessage::decode),
                SyncBlockItemMessage::handle
        ).add();
        Compat.withConsumer(
                registerMessage(OpenVillagerAdvancementsMenuMessage.class, NetworkDirection.PLAY_TO_CLIENT).
                        encoder(OpenVillagerAdvancementsMenuMessage::encode).
                        decoder(OpenVillagerAdvancementsMenuMessage::decode),
                OpenVillagerAdvancementsMenuMessage::handle
        ).add();
        Compat.withConsumer(
                registerMessage(OnScreenTextMessage.class, NetworkDirection.PLAY_TO_CLIENT).
                        encoder(OnScreenTextMessage::encode).
                        decoder(OnScreenTextMessage::decode),
                OnScreenTextMessage::handle
        ).add();
        Compat.withConsumer(
                registerMessage(JobWantedIngredientsMessage.class, NetworkDirection.PLAY_TO_CLIENT).
                        encoder(JobWantedIngredientsMessage::encode).
                        decoder(JobWantedIngredientsMessage::decode),
                JobWantedIngredientsMessage::handle
        ).add();
        Compat.withConsumer(
                registerMessage(SyncVillagerAdvancementsMessage.class, NetworkDirection.PLAY_TO_CLIENT).
                        encoder(SyncVillagerAdvancementsMessage::encode).
                        decoder(SyncVillagerAdvancementsMessage::decode),
                SyncVillagerAdvancementsMessage::handle
        ).add();
        Compat.withConsumer(
                registerMessage(CloseScreensMessage.class, NetworkDirection.PLAY_TO_CLIENT).
                        encoder(CloseScreensMessage::encode).
                        decoder(CloseScreensMessage::decode),
                CloseScreensMessage::handle
        ).add();
        Compat.withConsumer(
                registerMessage(MultiStatusScreenSyncMessage.class, NetworkDirection.PLAY_TO_CLIENT).
                        encoder(MultiStatusScreenSyncMessage::encode).
                        decoder(MultiStatusScreenSyncMessage::decode),
                MultiStatusScreenSyncMessage::handle
        ).add();
        Compat.withConsumer(
                registerMessage(EconomicsUpdate.class, NetworkDirection.PLAY_TO_CLIENT).
                        encoder(EconomicsUpdate::encode).
                        decoder(EconomicsUpdate::decode),
                EconomicsUpdate::handle
        ).add();
    }

    public static <T> SimpleChannel.MessageBuilder<T> registerMessage(
            Class<T> msgClass,
            NetworkDirection dir
    ) {
        return CHANNEL.messageBuilder(msgClass, messageIndex++, dir);
    }
}

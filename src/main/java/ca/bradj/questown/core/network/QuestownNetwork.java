package ca.bradj.questown.core.network;

import ca.bradj.questown.Questown;
import ca.bradj.questown.mc.Util;
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
        Util.withConsumer(
                registerMessage(AddWorkFromUIMessage.class, NetworkDirection.PLAY_TO_SERVER).
                encoder(AddWorkFromUIMessage::encode).
                decoder(AddWorkFromUIMessage::decode),
                AddWorkFromUIMessage::handle
        ).add();
        Util.withConsumer(
                registerMessage(RemoveWorkFromUIMessage.class, NetworkDirection.PLAY_TO_SERVER).
                encoder(RemoveWorkFromUIMessage::encode).
                decoder(RemoveWorkFromUIMessage::decode),
                RemoveWorkFromUIMessage::handle
        ).add();
        Util.withConsumer(
                registerMessage(OpenQuestsMenuMessage.class, NetworkDirection.PLAY_TO_SERVER).
                encoder(OpenQuestsMenuMessage::encode).
                decoder(OpenQuestsMenuMessage::decode),
                OpenQuestsMenuMessage::handle
        ).add();
        Util.withConsumer(
                registerMessage(RemoveQuestFromUIMessage.class, NetworkDirection.PLAY_TO_SERVER).
                encoder(RemoveQuestFromUIMessage::encode).
                decoder(RemoveQuestFromUIMessage::decode),
                RemoveQuestFromUIMessage::handle
        ).add();
        Util.withConsumer(
                registerMessage(OpenVillagerMenuMessage.class, NetworkDirection.PLAY_TO_SERVER).
                encoder(OpenVillagerMenuMessage::encode).
                decoder(OpenVillagerMenuMessage::decode),
                OpenVillagerMenuMessage::handle
        ).add();
        Util.withConsumer(
                registerMessage(SyncBlockItemMessage.class, NetworkDirection.PLAY_TO_CLIENT).
                encoder(SyncBlockItemMessage::encode).
                decoder(SyncBlockItemMessage::decode),
                SyncBlockItemMessage::handle
        ).add();
        Util.withConsumer(
                registerMessage(OpenVillagerAdvancementsMenuMessage.class, NetworkDirection.PLAY_TO_CLIENT).
                encoder(OpenVillagerAdvancementsMenuMessage::encode).
                decoder(OpenVillagerAdvancementsMenuMessage::decode),
                OpenVillagerAdvancementsMenuMessage::handle
        ).add();
    }

    public static <T> SimpleChannel.MessageBuilder<T> registerMessage(Class<T> msgClass, NetworkDirection dir) {
        return CHANNEL.messageBuilder(msgClass, messageIndex++, dir);
    }
}

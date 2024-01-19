package ca.bradj.questown.core.network;

import ca.bradj.questown.Questown;
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
        registerMessage(AddWorkFromUIMessage.class, NetworkDirection.PLAY_TO_SERVER).
                encoder(AddWorkFromUIMessage::encode).
                decoder(AddWorkFromUIMessage::decode).
                consumerNetworkThread(AddWorkFromUIMessage::handle).
                add();
        registerMessage(RemoveWorkFromUIMessage.class, NetworkDirection.PLAY_TO_SERVER).
                encoder(RemoveWorkFromUIMessage::encode).
                decoder(RemoveWorkFromUIMessage::decode).
                consumerNetworkThread(RemoveWorkFromUIMessage::handle).
                add();
        registerMessage(OpenQuestsMenuMessage.class, NetworkDirection.PLAY_TO_SERVER).
                encoder(OpenQuestsMenuMessage::encode).
                decoder(OpenQuestsMenuMessage::decode).
                consumerNetworkThread(OpenQuestsMenuMessage::handle).
                add();
        registerMessage(RemoveQuestFromUIMessage.class, NetworkDirection.PLAY_TO_SERVER).
                encoder(RemoveQuestFromUIMessage::encode).
                decoder(RemoveQuestFromUIMessage::decode).
                consumerNetworkThread(RemoveQuestFromUIMessage::handle).
                add();
        registerMessage(OpenVillagerMenuMessage.class, NetworkDirection.PLAY_TO_SERVER).
                encoder(OpenVillagerMenuMessage::encode).
                decoder(OpenVillagerMenuMessage::decode).
                consumerNetworkThread(OpenVillagerMenuMessage::handle).
                add();
    }

    public static <T> SimpleChannel.MessageBuilder<T> registerMessage(Class<T> msgClass, NetworkDirection dir) {
        return CHANNEL.messageBuilder(msgClass, messageIndex++, dir);
    }
}

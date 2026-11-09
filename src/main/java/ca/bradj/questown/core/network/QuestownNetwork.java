package ca.bradj.questown.core.network;

import ca.bradj.questown.QT;
import ca.bradj.questown.gui.town.status.MultiStatusScreenSyncMessage;
import ca.bradj.questown.loader.Channel;
import ca.bradj.questown.loader.Loader;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Mod network. The messages are loader-neutral (an encoder/decoder over {@code FriendlyByteBuf}
 * and a {@code Channel.Context} handler); the active loader supplies the channel.
 *
 * <p>{@link #init()} must run before the loader registers its channel.
 */
public final class QuestownNetwork {

    public static final String NETWORK_VERSION = "1";
    public static final String CHANNEL_ID = "questown:network";

    @Nullable
    private static Channel channel;

    private QuestownNetwork() {
    }

    public static void init() {
        if (channel != null) {
            return;
        }
        channel = Loader.channel(CHANNEL_ID);
        register(Channel.Direction.TO_CLIENT, CloseScreensMessage.class, CloseScreensMessage::encode, CloseScreensMessage::decode, CloseScreensMessage::handle);
        register(Channel.Direction.TO_CLIENT, OpenDormantFlagMessage.class, OpenDormantFlagMessage::encode, OpenDormantFlagMessage::decode, OpenDormantFlagMessage::handle);
        register(Channel.Direction.TO_CLIENT, EconomicsUpdate.class, EconomicsUpdate::encode, EconomicsUpdate::decode, EconomicsUpdate::handle);
        register(Channel.Direction.TO_CLIENT, JobWantedIngredientsMessage.class, JobWantedIngredientsMessage::encode, JobWantedIngredientsMessage::decode, JobWantedIngredientsMessage::handle);
        register(Channel.Direction.TO_CLIENT, MultiStatusScreenSyncMessage.class, MultiStatusScreenSyncMessage::encode, MultiStatusScreenSyncMessage::decode, MultiStatusScreenSyncMessage::handle);
        register(Channel.Direction.TO_CLIENT, OnScreenTextMessage.class, OnScreenTextMessage::encode, OnScreenTextMessage::decode, OnScreenTextMessage::handle);
        register(Channel.Direction.TO_CLIENT, OpenRelocationConfirmMessage.class, OpenRelocationConfirmMessage::encode, OpenRelocationConfirmMessage::decode, OpenRelocationConfirmMessage::handle);
        register(Channel.Direction.TO_CLIENT, OpenVillagerAdvancementsMenuMessage.class, OpenVillagerAdvancementsMenuMessage::encode, OpenVillagerAdvancementsMenuMessage::decode, OpenVillagerAdvancementsMenuMessage::handle);
        register(Channel.Direction.TO_CLIENT, ShowItemJobsMessage.class, ShowItemJobsMessage::encode, ShowItemJobsMessage::decode, ShowItemJobsMessage::handle);
        register(Channel.Direction.TO_CLIENT, ShowSkillDetailsMessage.class, ShowSkillDetailsMessage::encode, ShowSkillDetailsMessage::decode, ShowSkillDetailsMessage::handle);
        register(Channel.Direction.TO_CLIENT, ShowTutorialToastMessage.class, ShowTutorialToastMessage::encode, ShowTutorialToastMessage::decode, ShowTutorialToastMessage::handle);
        register(Channel.Direction.TO_CLIENT, ShowPathStartedToastMessage.class, ShowPathStartedToastMessage::encode, ShowPathStartedToastMessage::decode, ShowPathStartedToastMessage::handle);
        register(Channel.Direction.TO_CLIENT, SyncBlockItemMessage.class, SyncBlockItemMessage::encode, SyncBlockItemMessage::decode, SyncBlockItemMessage::handle);
        register(Channel.Direction.TO_CLIENT, SyncStatusArtMessage.class, SyncStatusArtMessage::encode, SyncStatusArtMessage::decode, SyncStatusArtMessage::handle);
        register(Channel.Direction.TO_CLIENT, SyncStatusTextMessage.class, SyncStatusTextMessage::encode, SyncStatusTextMessage::decode, SyncStatusTextMessage::handle);
        register(Channel.Direction.TO_CLIENT, SyncVillagerAdvancementsMessage.class, SyncVillagerAdvancementsMessage::encode, SyncVillagerAdvancementsMessage::decode, SyncVillagerAdvancementsMessage::handle);
        register(Channel.Direction.TO_CLIENT, SyncWorkForCommandsMessage.class, SyncWorkForCommandsMessage::encode, SyncWorkForCommandsMessage::decode, SyncWorkForCommandsMessage::handle);
        register(Channel.Direction.TO_CLIENT, WorkRequestConfirmMessage.class, WorkRequestConfirmMessage::encode, WorkRequestConfirmMessage::decode, WorkRequestConfirmMessage::handle);
        register(Channel.Direction.TO_SERVER, AddWorkFromUIMessage.class, AddWorkFromUIMessage::encode, AddWorkFromUIMessage::decode, AddWorkFromUIMessage::handle);
        register(Channel.Direction.TO_SERVER, BeginTownRelocationMessage.class, BeginTownRelocationMessage::encode, BeginTownRelocationMessage::decode, BeginTownRelocationMessage::handle);
        register(Channel.Direction.TO_SERVER, CancelTownShutdownMessage.class, CancelTownShutdownMessage::encode, CancelTownShutdownMessage::decode, CancelTownShutdownMessage::handle);
        register(Channel.Direction.TO_SERVER, DormantFlagChoiceMessage.class, DormantFlagChoiceMessage::encode, DormantFlagChoiceMessage::decode, DormantFlagChoiceMessage::handle);
        register(Channel.Direction.TO_SERVER, ChangeVillagerJobMessage.class, ChangeVillagerJobMessage::encode, ChangeVillagerJobMessage::decode, ChangeVillagerJobMessage::handle);
        register(Channel.Direction.TO_SERVER, CreateStockRequestFromUIMessage.class, CreateStockRequestFromUIMessage::encode, CreateStockRequestFromUIMessage::decode, CreateStockRequestFromUIMessage::handle);
        register(Channel.Direction.TO_SERVER, FlagCraftMessage.class, FlagCraftMessage::encode, FlagCraftMessage::decode, FlagCraftMessage::handle);
        register(Channel.Direction.TO_SERVER, ClaimFreeFoodMessage.class, ClaimFreeFoodMessage::encode, ClaimFreeFoodMessage::decode, ClaimFreeFoodMessage::handle);
        register(Channel.Direction.TO_SERVER, GiveBOPMessage.class, GiveBOPMessage::encode, GiveBOPMessage::decode, GiveBOPMessage::handle);
        register(Channel.Direction.TO_SERVER, JobRootChangeMessage.class, JobRootChangeMessage::encode, JobRootChangeMessage::decode, JobRootChangeMessage::handle);
        register(Channel.Direction.TO_SERVER, OpenFlagMenuMessage.class, OpenFlagMenuMessage::encode, OpenFlagMenuMessage::decode, OpenFlagMenuMessage::handle);
        register(Channel.Direction.TO_SERVER, OpenItemJobsMessage.class, OpenItemJobsMessage::encode, OpenItemJobsMessage::decode, OpenItemJobsMessage::handle);
        register(Channel.Direction.TO_SERVER, OpenJobMessage.class, OpenJobMessage::encode, OpenJobMessage::decode, OpenJobMessage::handle);
        register(Channel.Direction.TO_SERVER, OpenSkillDetailsMessage.class, OpenSkillDetailsMessage::encode, OpenSkillDetailsMessage::decode, OpenSkillDetailsMessage::handle);
        register(Channel.Direction.TO_SERVER, SpendFlagBopOnSkillMessage.class, SpendFlagBopOnSkillMessage::encode, SpendFlagBopOnSkillMessage::decode, SpendFlagBopOnSkillMessage::handle);
        register(Channel.Direction.TO_SERVER, OpenQuestsMenuMessage.class, OpenQuestsMenuMessage::encode, OpenQuestsMenuMessage::decode, OpenQuestsMenuMessage::handle);
        register(Channel.Direction.TO_SERVER, OpenVillagerMenuMessage.class, OpenVillagerMenuMessage::encode, OpenVillagerMenuMessage::decode, OpenVillagerMenuMessage::handle);
        register(Channel.Direction.TO_SERVER, RelocationChoiceMessage.class, RelocationChoiceMessage::encode, RelocationChoiceMessage::decode, RelocationChoiceMessage::handle);
        register(Channel.Direction.TO_SERVER, RemoveQuestFromUIMessage.class, RemoveQuestFromUIMessage::encode, RemoveQuestFromUIMessage::decode, RemoveQuestFromUIMessage::handle);
        register(Channel.Direction.TO_SERVER, RemoveWorkFromUIMessage.class, RemoveWorkFromUIMessage::encode, RemoveWorkFromUIMessage::decode, RemoveWorkFromUIMessage::handle);
        register(Channel.Direction.TO_SERVER, SetRequestLimitMessage.class, SetRequestLimitMessage::encode, SetRequestLimitMessage::decode, SetRequestLimitMessage::handle);
        register(Channel.Direction.TO_SERVER, ToggleBoardAutoRequestMessage.class, ToggleBoardAutoRequestMessage::encode, ToggleBoardAutoRequestMessage::decode, ToggleBoardAutoRequestMessage::handle);
        register(Channel.Direction.TO_SERVER, UnlockJobMessage.class, UnlockJobMessage::encode, UnlockJobMessage::decode, UnlockJobMessage::handle);
        QT.LOGGER.info("[questown] network ready: 32 message types registered");
    }

    private static <T> void register(
            Channel.Direction dir,
            Class<T> type,
            Channel.Encoder<T> encoder,
            Channel.Decoder<T> decoder,
            Channel.Handler<T> handler
    ) {
        channel.message(type, dir)
                .encoder(encoder)
                .decoder(decoder)
                .handler(handler);
    }

    public static Channel channel() {
        return Objects.requireNonNull(channel, "QuestownNetwork.init() has not run yet");
    }

    public static <T> void sendToPlayer(Player p, T msg) {
        channel().sendToPlayer(p, msg);
    }

    public static <T> void sendToAll(Level level, T msg) {
        channel().sendToAll(level, msg);
    }

    public static <T> void sendToServer(T msg) {
        channel().sendToServer(msg);
    }
}

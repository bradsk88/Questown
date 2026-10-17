package ca.bradj.questown.gui;

import ca.bradj.questown.QT;
import ca.bradj.questown.core.network.OpenItemJobsMessage;
import ca.bradj.questown.core.network.QuestownNetwork;
import net.minecraft.world.item.crafting.Ingredient;
import ca.bradj.questown.core.network.RemoveQuestFromUIMessage;
import ca.bradj.questown.town.special.SpecialQuests;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

import ca.bradj.questown.core.UtilClean;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public abstract class AbstractQuestsContainer extends AbstractContainerMenu {
    protected final Collection<UIQuest> quests;
    protected final FlagTabsEmbedding.FlagInfo flagInfo;
    /**
     * villager UUID -&gt; "Villager N" display name, synced with the menu (the 2026-10-14
     * display-name work; empty for menus that do not carry it, tooltips fall back to the
     * truncated UUID).
     */
    protected final Map<UUID, String> villagerDisplayNames;

    public AbstractQuestsContainer(
            MenuType<?> townQuestsContainerMenuType,
            int windowId,
            Collection<UIQuest> quests,
            FlagTabsEmbedding.FlagInfo flagInfo
    ) {
        this(townQuestsContainerMenuType, windowId, quests, flagInfo, Map.of());
    }

    protected AbstractQuestsContainer(
            MenuType<?> townQuestsContainerMenuType,
            int windowId,
            Collection<UIQuest> quests,
            FlagTabsEmbedding.FlagInfo flagInfo,
            Map<UUID, String> villagerDisplayNames
    ) {
        super(townQuestsContainerMenuType, windowId);
        this.quests = quests;
        this.flagInfo = flagInfo;
        this.villagerDisplayNames = villagerDisplayNames == null ? Map.of() : villagerDisplayNames;
    }

    public Map<UUID, String> villagerDisplayNames() {
        return this.villagerDisplayNames;
    }

    /**
     * "Villager N" when this sync carries a display name for the owner, otherwise the
     * truncated UUID (the historical tooltip form).
     */
    public @NotNull String villagerLabelFor(String villagerUuidString) {
        return villagerLabelFor(this.villagerDisplayNames, villagerUuidString);
    }

    public static @NotNull String villagerLabelFor(
            Map<UUID, String> displayNames,
            String villagerUuidString
    ) {
        if (!villagerUuidString.isEmpty()) {
            try {
                String name = displayNames.get(UUID.fromString(villagerUuidString));
                if (name != null && !name.isEmpty()) {
                    return name;
                }
            } catch (IllegalArgumentException ignored) {
                // not a full UUID string - fall through to the truncated form
            }
        }
        return UtilClean.truncateMiddle(villagerUuidString);
    }

    protected static void writeVillagerNames(
            FriendlyByteBuf data,
            Map<UUID, String> names
    ) {
        data.writeVarInt(names.size());
        for (Map.Entry<UUID, String> e : names.entrySet()) {
            data.writeUUID(e.getKey());
            data.writeUtf(e.getValue());
        }
    }

    public static Map<UUID, String> readVillagerNames(FriendlyByteBuf data) {
        int n = data.readVarInt();
        Map<UUID, String> out = new HashMap<>(Math.max(n, 1));
        for (int i = 0; i < n; i++) {
            out.put(data.readUUID(), data.readUtf());
        }
        return out;
    }

    protected static void writeQuests(
            FriendlyByteBuf data,
            List<UIQuest> quests
    ) {
        UIQuest.Serializer ser = new UIQuest.Serializer();
        data.writeInt(quests.size());
        data.writeCollection(
                quests, (buf, q) -> {
                    ResourceLocation id;
                    if (q == null) {
                        q = UIQuest.BROKEN;
                        id = SpecialQuests.BROKEN;
                    } else {
                        id = q.getWantedId();
                    }
                    buf.writeResourceLocation(id);
                    ser.toNetwork(buf, q);
                }
        );
    }

    public static Collection<UIQuest> readQuests(FriendlyByteBuf data) {
        try {
            int size = data.readInt();
            ArrayList<UIQuest> r = data.readCollection(
                    c -> new ArrayList<>(size), buf -> {
                        ResourceLocation recipeID = buf.readResourceLocation();
                        return new UIQuest.Serializer().fromNetwork(recipeID, buf);
                    }
            );
            r.sort(UIQuest::compareTo);
            return r;
        } catch (Exception e) {
            QT.GUI_LOGGER.error("Failed to read quests", e);
            throw e;
        }
    }

    protected static void writeFlagPos(
            FriendlyByteBuf data,
            BlockPos pos
    ) {
        data.writeInt(pos.getX());
        data.writeInt(pos.getY());
        data.writeInt(pos.getZ());
    }

    public static BlockPos readFlagPos(FriendlyByteBuf data) {
        try {
            return new BlockPos(data.readInt(), data.readInt(), data.readInt());
        } catch (Exception e) {
            QT.GUI_LOGGER.error("Failed to read flag pos", e);
            throw e;
        }
    }

    @Override
    public boolean stillValid(Player p_38874_) {
        return true;
    }

    public Collection<UIQuest> GetQuests() {
        return this.quests;
    }

    public boolean isAllComplete() {
        return false;
    }

    public boolean isMorningRewardPending() {
        return false;
    }

    public void sendRemoveRequest(UUID batchUUID) {
        BlockPos f = flagInfo.flagPos();
        QuestownNetwork.sendToServer(
                new RemoveQuestFromUIMessage(batchUUID, f.getX(), f.getY(), f.getZ(), true)
        );
    }

    /** The "jobs that make this item" screen for a quest card's item/tag icon (server answers). */
    public void sendOpenItemJobs(Ingredient wanted) {
        QuestownNetwork.sendToServer(new OpenItemJobsMessage(flagInfo.flagPos(), wanted));
    }

    @Override
    public ItemStack quickMoveStack(
            Player p_38941_,
            int p_38942_
    ) {
        return ItemStack.EMPTY;
    }
}

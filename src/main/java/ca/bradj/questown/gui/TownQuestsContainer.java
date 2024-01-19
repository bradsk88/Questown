package ca.bradj.questown.gui;

import ca.bradj.questown.core.init.MenuTypesInit;
import ca.bradj.questown.core.network.QuestownNetwork;
import ca.bradj.questown.core.network.RemoveQuestFromUIMessage;
import ca.bradj.questown.town.quests.Quest;
import ca.bradj.questown.town.special.SpecialQuests;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public class TownQuestsContainer extends AbstractContainerMenu {

    private final Collection<UIQuest> quests;
    final BlockPos flagPos;

    public TownQuestsContainer(
            int windowId,
            Collection<UIQuest> quests,
            BlockPos flagPos
    ) {
        super(MenuTypesInit.TOWN_QUESTS.get(), windowId);
        this.quests = quests;
        this.flagPos = flagPos;
    }

    public TownQuestsContainer(
            int windowId,
            Inventory inv,
            FriendlyByteBuf data
    ) {
        this(windowId, readQuests(data), readFlagPos(data));
    }

    public static void write(
            FriendlyByteBuf data,
            List<UIQuest> quests,
            BlockPos pos
    ) {
        writeQuests(data, quests);
        writeFlagPos(data, pos);
    }

    private static void writeQuests(
            FriendlyByteBuf data,
            List<UIQuest> quests
    ) {
        UIQuest.Serializer ser = new UIQuest.Serializer();
        data.writeInt(quests.size());
        data.writeCollection(quests, (buf, q) -> {
            ResourceLocation id;
            if (q == null) {
                id = SpecialQuests.BROKEN;
                q = new UIQuest(null, SpecialQuests.SPECIAL_QUESTS.get(id), Quest.QuestStatus.ACTIVE, null, null, null);
            } else {
                id = q.getRecipeId();
            }
            buf.writeResourceLocation(id);
            ser.toNetwork(buf, q);
        });
    }

    public static Collection<UIQuest> readQuests(FriendlyByteBuf data) {
        int size = data.readInt();
        ArrayList<UIQuest> r = data.readCollection(c -> new ArrayList<>(size), buf -> {
            ResourceLocation recipeID = buf.readResourceLocation();
            return new UIQuest.Serializer().fromNetwork(recipeID, buf);
        });
        r.sort(UIQuest::compareTo);
        return r;
    }

    private static void writeFlagPos(
            FriendlyByteBuf data,
            BlockPos pos
    ) {
        data.writeInt(pos.getX());
        data.writeInt(pos.getY());
        data.writeInt(pos.getZ());
    }

    public static BlockPos readFlagPos(FriendlyByteBuf data) {
        return new BlockPos(data.readInt(), data.readInt(), data.readInt());
    }

    @Override
    public boolean stillValid(Player p_38874_) {
        return true;
    }

    public Collection<UIQuest> GetQuests() {
        return this.quests;
    }

    public void sendRemoveRequest(UUID batchUUID) {
        QuestownNetwork.CHANNEL.sendToServer(
                new RemoveQuestFromUIMessage(batchUUID, flagPos.getX(), flagPos.getY(), flagPos.getZ(), true)
        );
    }

    @Override
    public ItemStack quickMoveStack(
            Player p_38941_,
            int p_38942_
    ) {
        return ItemStack.EMPTY;
    }
}
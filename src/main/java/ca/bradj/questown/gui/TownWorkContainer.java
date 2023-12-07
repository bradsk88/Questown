package ca.bradj.questown.gui;

import ca.bradj.questown.core.init.MenuTypesInit;
import ca.bradj.questown.core.network.QuestownNetwork;
import ca.bradj.questown.core.network.RemoveWorkFromUIMessage;
import ca.bradj.questown.jobs.requests.WorkRequest;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collection;

public class TownWorkContainer extends AbstractContainerMenu {

    private final Collection<UIWork> work;
    public final ca.bradj.questown.gui.AddWorkContainer addWorkContainer;
    private final BlockPos flag;

    public static TownWorkContainer ForClientSide(
            int windowId,
            Inventory inv,
            FriendlyByteBuf buf
    ) {
        AddWorkContainer qMenu = new AddWorkContainer(
                windowId,
                AddWorkContainer.readWorkResults(buf),
                AddWorkContainer.readFlagPosition(buf)
        );
        return new TownWorkContainer(
                windowId, readWork(buf), qMenu,
                readFlagPosition(buf)
        );
    }

    private static BlockPos readFlagPosition(FriendlyByteBuf buf) {
        return AddWorkContainer.readFlagPosition(buf);
    }

    public TownWorkContainer(
            int windowId,
            Collection<UIWork> quests,
            AddWorkContainer awc,
            BlockPos flag
    ) {
        super(MenuTypesInit.TOWN_WORK.get(), windowId);
        this.work = quests;
        this.addWorkContainer = awc;
        this.flag = flag;
    }

    public static Collection<UIWork> readWork(FriendlyByteBuf data) {
        int size = data.readInt();
        return data.readCollection(
                c -> new ArrayList<>(size),
                buf -> new UIWork(WorkRequest.fromNetwork(buf))
        );
    }

    public static void writeWork(
            Collection<WorkRequest> requestedResults,
            FriendlyByteBuf data
    ) {
        data.writeInt(requestedResults.size());
        data.writeCollection(requestedResults, (buf, w) -> w.toNetwork(buf));
    }

    public static void writeFlagPosition(
            BlockPos townFlagBasePos,
            FriendlyByteBuf data
    ) {
        AddWorkContainer.writeFlagPosition(townFlagBasePos, data);
    }

    @Override
    public boolean stillValid(Player p_38874_) {
        return true;
    }

    public Collection<UIWork> getWork() {
        return work;
    }

    public void sendRemoveRequest(UIWork jobPosting) {
        QuestownNetwork.CHANNEL.sendToServer(
                new RemoveWorkFromUIMessage(jobPosting.getResultWanted(), flag.getX(), flag.getY(), flag.getZ())
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
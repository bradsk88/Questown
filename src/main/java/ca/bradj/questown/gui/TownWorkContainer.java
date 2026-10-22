package ca.bradj.questown.gui;

import ca.bradj.questown.core.init.MenuTypesInit;
import ca.bradj.questown.core.network.QuestownNetwork;
import ca.bradj.questown.core.network.RemoveWorkFromUIMessage;
import ca.bradj.questown.core.network.ToggleBoardAutoRequestMessage;
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
    private boolean skipToAdd;
    // F2: the flag's "auto-request" toggle, mirrored client-side so the board screen can
    // render it. Optimistically flipped on click; the server re-opens the menu afterwards
    // so any drift resyncs.
    private boolean boardAutoRequest;
    // The flag's top unmet-need display name (or empty when there is none), so the board's
    // empty state can point at posting it instead of a vague teaser (job-board-empty-state.md).
    private final String topUnmetNeedName;

    public static TownWorkContainer ForClientSide(
            int windowId,
            Inventory inv,
            FriendlyByteBuf buf
    ) {
        AddWorkContainer qMenu = new AddWorkContainer(
                windowId,
                AddWorkContainer.readWorkResults(buf),
                AddWorkContainer.readCategories(buf),
                AddWorkContainer.readFlagPosition(buf)
        );
        return new TownWorkContainer(
                windowId, readWork(buf), qMenu,
                readFlagPosition(buf),
                buf.readBoolean(),
                buf.readBoolean(),
                buf.readUtf()
        );
    }

    private static BlockPos readFlagPosition(FriendlyByteBuf buf) {
        return AddWorkContainer.readFlagPosition(buf);
    }

    public TownWorkContainer(
            int windowId,
            Collection<UIWork> quests,
            AddWorkContainer awc,
            BlockPos flag,
            boolean skipToAdd,
            boolean boardAutoRequest
    ) {
        this(windowId, quests, awc, flag, skipToAdd, boardAutoRequest, "");
    }

    public TownWorkContainer(
            int windowId,
            Collection<UIWork> quests,
            AddWorkContainer awc,
            BlockPos flag,
            boolean skipToAdd,
            boolean boardAutoRequest,
            String topUnmetNeedName
    ) {
        super(MenuTypesInit.TOWN_WORK.get(), windowId);
        this.work = quests;
        this.addWorkContainer = awc;
        this.flag = flag;
        this.skipToAdd = skipToAdd;
        this.boardAutoRequest = boardAutoRequest;
        this.topUnmetNeedName = topUnmetNeedName == null ? "" : topUnmetNeedName;
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
    public ItemStack quickMoveStack(
            Player player,
            int i
    ) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player p_38874_) {
        return true;
    }

    public Collection<UIWork> getWork() {
        return work;
    }

    public void sendRemoveRequest(UIWork jobPosting) {
        QuestownNetwork.sendToServer(
                new RemoveWorkFromUIMessage(jobPosting.getResultWanted(), flag.getX(), flag.getY(), flag.getZ())
        );
    }

    public boolean skipToAdd() {
        return this.skipToAdd;
    }

    public boolean isBoardAutoRequest() {
        return boardAutoRequest;
    }

    /** The flag's top unmet-need display name; empty string when there is none. */
    public String getTopUnmetNeedName() {
        return topUnmetNeedName;
    }

    public void setBoardAutoRequest(boolean v) {
        this.boardAutoRequest = v;
    }

    public void sendToggleBoardAutoRequest() {
        QuestownNetwork.sendToServer(
                new ToggleBoardAutoRequestMessage(boardAutoRequest, flag.getX(), flag.getY(), flag.getZ())
        );
    }
}
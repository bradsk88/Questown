package ca.bradj.questown.gui;

import ca.bradj.questown.core.init.MenuTypesInit;
import ca.bradj.questown.core.network.CreateStockRequestFromUIMessage;
import ca.bradj.questown.core.network.QuestownNetwork;
import ca.bradj.questown.jobs.requests.WorkRequest;
import com.google.common.collect.ImmutableCollection;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.ArrayList;
import java.util.Collection;
import java.util.UUID;

public class CreateStockRequestContainer extends AbstractContainerMenu {

    private final Collection<Ingredient> work;
    private final BlockPos flag;

    public CreateStockRequestContainer(
            int windowId,
            Collection<Ingredient> work,
            BlockPos flag
    ) {
        super(MenuTypesInit.CREATE_STOCK_REQUEST.get(), windowId);
        this.work = work;
        this.flag = flag;
    }

    public static CreateStockRequestContainer ForClientSide(int i, Inventory inventory, FriendlyByteBuf buf) {
        return new CreateStockRequestContainer(
                i, readWorkResults(buf), readFlagPosition(buf)
        );
    }

    public static Collection<Ingredient> readWorkResults(FriendlyByteBuf data) {
        int size = data.readInt();
        ArrayList<Ingredient> r = data.readCollection(
                c -> new ArrayList<>(size),
                Ingredient::fromNetwork
        );
        return r;
    }

    public static void writeWorkResults(ImmutableCollection<Ingredient> allOutputs, FriendlyByteBuf data) {
        data.writeInt(allOutputs.size());
        data.writeCollection(allOutputs, (v, i) -> i.toNetwork(v));
    }

    public static BlockPos readFlagPosition(FriendlyByteBuf buf) {
        return buf.readBlockPos();
    }

    public static void writeFlagPosition(
            BlockPos blockPos,
            FriendlyByteBuf data
    ) {
        data.writeBlockPos(blockPos);
    }

    @Override
    public boolean stillValid(Player p_38874_) {
        return true;
    }

    public Collection<Ingredient> getAddableWork() {
        return work;
    }

    public void sendRequest(WorkRequest item, UUID uuid) {
        QuestownNetwork.CHANNEL.sendToServer(
                new CreateStockRequestFromUIMessage(item, flag.getX(), flag.getY(), flag.getZ(), uuid)
        );
    }
}

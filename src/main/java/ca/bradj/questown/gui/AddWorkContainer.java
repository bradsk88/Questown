package ca.bradj.questown.gui;

import ca.bradj.questown.core.init.MenuTypesInit;
import ca.bradj.questown.core.network.AddWorkFromUIMessage;
import ca.bradj.questown.core.network.QuestownNetwork;
import com.google.common.collect.ImmutableCollection;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.ArrayList;
import java.util.Collection;

public class AddWorkContainer extends AbstractContainerMenu {

    private final Collection<Ingredient> work;
    private final BlockPos flag;

    public AddWorkContainer(
            int windowId,
            Collection<Ingredient> quests,
            BlockPos flag
    ) {
        super(MenuTypesInit.TOWN_WORK.get(), windowId);
        this.work = quests;
        this.flag = flag;
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

    public void sendRequest(ItemStack item) {
        QuestownNetwork.CHANNEL.sendToServer(
                new AddWorkFromUIMessage(item, flag.getX(), flag.getY(), flag.getZ())
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
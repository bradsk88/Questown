package ca.bradj.questown.gui;

import ca.bradj.questown.core.init.MenuTypesInit;
import com.google.common.collect.ImmutableList;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.ArrayList;
import java.util.Collection;

public class AddWorkContainer extends AbstractContainerMenu {

    private final Collection<Ingredient> work;

    public AddWorkContainer(
            int windowId,
            Collection<Ingredient> quests
    ) {
        super(MenuTypesInit.TOWN_WORK.get(), windowId);
        this.work = quests;
    }

    public AddWorkContainer(
            int windowId,
            Inventory inv,
            FriendlyByteBuf data
    ) {
        this(windowId, readWorkResults(data));
    }

    public static Collection<Ingredient> readWorkResults(FriendlyByteBuf data) {
        int size = data.readInt();
        ArrayList<Ingredient> r = data.readCollection(
                c -> new ArrayList<>(size),
                Ingredient::fromNetwork
        );
        return r;
    }

    public static void writeWorkResults(ImmutableList<Ingredient> allOutputs, FriendlyByteBuf data) {
        data.writeInt(allOutputs.size());
        data.writeCollection(allOutputs, (v, i) -> i.toNetwork(v));
    }

    @Override
    public boolean stillValid(Player p_38874_) {
        return true;
    }

    public Collection<Ingredient> getAddableWork() {
        return work;
    }
}
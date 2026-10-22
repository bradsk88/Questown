package ca.bradj.questown.gui;

import ca.bradj.questown.core.init.MenuTypesInit;
import ca.bradj.questown.core.network.AddWorkFromUIMessage;
import ca.bradj.questown.core.network.QuestownNetwork;
import com.google.common.collect.ImmutableCollection;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class AddWorkContainer extends AbstractContainerMenu {

    private final Collection<Ingredient> work;
    // Request categories ("Food"), as item tag ids: an Ingredient crosses the wire as its item
    // list, so a tag would arrive as an anonymous list the client cannot name or send back.
    private final List<ResourceLocation> categories;
    private final BlockPos flag;

    public AddWorkContainer(
            int windowId,
            Collection<Ingredient> quests,
            List<ResourceLocation> categories,
            BlockPos flag
    ) {
        super(MenuTypesInit.TOWN_WORK.get(), windowId);
        this.work = quests;
        this.categories = categories;
        this.flag = flag;
    }

    public static Collection<Ingredient> readWorkResults(FriendlyByteBuf data) {
        int size = data.readInt();
        ArrayList<Ingredient> r = data.readCollection(c -> new ArrayList<>(size), ca.bradj.questown.mc.Compat::readIngredient);
        return r;
    }

    public static void writeWorkResults(
            ImmutableCollection<Ingredient> allOutputs,
            FriendlyByteBuf data
    ) {
        data.writeInt(allOutputs.size());
        data.writeCollection(allOutputs, ca.bradj.questown.mc.Compat::writeIngredient);
    }

    public static List<ResourceLocation> readCategories(FriendlyByteBuf data) {
        return data.readList(FriendlyByteBuf::readResourceLocation);
    }

    public static void writeCategories(
            List<ResourceLocation> categories,
            FriendlyByteBuf data
    ) {
        data.writeCollection(categories, FriendlyByteBuf::writeResourceLocation);
    }

    /** The categories as tag ingredients, rebuilt on this side so they keep their tag. */
    public List<Ingredient> getCategories() {
        return categories.stream().map(c -> Ingredient.of(ca.bradj.questown.mc.Compat.itemTag(c))).toList();
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

    public Collection<Ingredient> getAddableWork() {
        return work;
    }

    public void sendRequest(Ingredient item) {
        AddWorkFromUIMessage msg = new AddWorkFromUIMessage(item, flag, AddWorkFromUIMessage.Action.INQUIRED);
        QuestownNetwork.sendToServer(msg);
    }

    public boolean canRequest(Ingredient item) {
        return AddWorkFromUIMessage.canEncode(item);
    }
}
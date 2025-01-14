package ca.bradj.questown.blocks.entity;

import ca.bradj.questown.core.init.TagsInit;
import ca.bradj.questown.core.init.TilesInit;
import ca.bradj.questown.core.network.QuestownNetwork;
import ca.bradj.questown.core.network.SyncBlockItemMessage;
import ca.bradj.questown.integration.minecraft.MCTownItem;
import ca.bradj.questown.jobs.leaver.ContainerTarget;
import ca.bradj.questown.jobs.leaver.RankBoost;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.network.PacketDistributor;

import java.util.Collection;

public class FoodDisplayEntity extends BlockEntity implements ItemAccepting<MCTownItem>,
        ContainerTarget.Container<MCTownItem> {
    private NonNullList<ItemStack> items = NonNullList.withSize(4, ItemStack.EMPTY);

    public FoodDisplayEntity(
            BlockPos p_155229_,
            BlockState p_155230_
    ) {
        super(TilesInit.FOOD_DISPLAY.get(), p_155229_, p_155230_);
    }

    @Override
    public void load(CompoundTag p_155349_) {
        super.load(p_155349_);
        this.items = NonNullList.withSize(4, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(p_155349_, this.items);
        for (int i = 0; i < this.items.size(); i++) {
            SyncBlockItemMessage message = new SyncBlockItemMessage(getBlockPos(), this.items.get(i), i);
            QuestownNetwork.CHANNEL.send(PacketDistributor.ALL.noArg(), message);
        }
    }

    protected void saveAdditional(CompoundTag p_187489_) {
        super.saveAdditional(p_187489_);
        ContainerHelper.saveAllItems(p_187489_, this.items);
    }

    public Collection<ItemStack> getItems() {
        return items;
    }

    public boolean addFood(ItemStack itemStack) {
        for (int i = 0; i < items.size(); i++) {
            if (doSetItem(i, MCTownItem.fromMCItemStack(itemStack))) {
                SyncBlockItemMessage message = new SyncBlockItemMessage(getBlockPos(), itemStack, i);
                QuestownNetwork.CHANNEL.send(PacketDistributor.ALL.noArg(), message);
                return true;
            }
        }
        return false;
    }

    public ItemStack removeFood() {
        for (int i = 0; i < items.size(); i++) {
            MCTownItem removed = removeItem(i);
            if (removed != null && !removed.isEmpty()) {
                SyncBlockItemMessage message = new SyncBlockItemMessage(getBlockPos(), ItemStack.EMPTY, i);
                QuestownNetwork.CHANNEL.send(PacketDistributor.ALL.noArg(), message);
                return removed.toItemStack();
            }
        }
        return ItemStack.EMPTY;
    }

    @Override
    public MCTownItem removeItem(int index) {
        ItemStack itemStack = items.get(index);
        if (itemStack.isEmpty()) {
            return MCTownItem.Air();
        }
        items.set(index, ItemStack.EMPTY);
        return MCTownItem.fromMCItemStack(itemStack);
    }

    @Override
    public boolean setItem(
            int index,
            MCTownItem item
    ) {
        boolean didSet = doSetItem(index, item);
        if (didSet) {
            SyncBlockItemMessage message = new SyncBlockItemMessage(getBlockPos(), item.toItemStack(), index);
            QuestownNetwork.CHANNEL.send(PacketDistributor.ALL.noArg(), message);
        }
        return didSet;
    }

    public boolean doSetItem(
            int index,
            MCTownItem item
    ) {

        if (item.isEmpty()) {
            if (items.get(index).isEmpty()) {
                return false;
            }
            items.set(index, item.toItemStack());
            return true;
        }
        if (items.get(index).isEmpty()) {
            items.set(index, item.toItemStack());
            return true;
        }
        return false;
    }

    @Override
    public int size() {
        return items.size();
    }

    @Override
    public MCTownItem getItem(int i) {
        return MCTownItem.fromMCItemStack(items.get(i));
    }

    @Override
    public boolean isFull(
            ) {
        return items.stream().noneMatch(ItemStack::isEmpty);
    }

    @Override
    public String toShortString() {
        return items.toString();
    }

    @Override
    public String toShortString(boolean includeAir) {
        return items.toString();
    }

    @Override
    public boolean canAcceptIfSpaceAllows(MCTownItem item) {
        return Ingredient.of(TagsInit.Items.VILLAGER_FOOD).test(item.toItemStack());
    }

    @Override
    public RankBoost getItemAcceptanceRankBoost() {
        return RankBoost.SLIGHTLY_PREFERRED;
    }
}

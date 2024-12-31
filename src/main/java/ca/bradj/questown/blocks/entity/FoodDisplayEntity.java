package ca.bradj.questown.blocks.entity;

import ca.bradj.questown.core.init.TilesInit;
import ca.bradj.questown.core.network.QuestownNetwork;
import ca.bradj.questown.core.network.SyncBlockItemMessage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.network.PacketDistributor;

import java.util.Collection;

public class FoodDisplayEntity extends BlockEntity implements ItemAccepting {
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
            if (setItem(itemStack, i)) {
                SyncBlockItemMessage message = new SyncBlockItemMessage(getBlockPos(), itemStack, i);
                QuestownNetwork.CHANNEL.send(PacketDistributor.ALL.noArg(), message);
                return true;
            }
        }
        return false;
    }

    public ItemStack removeFood() {
        for (int i = 0; i < items.size(); i++) {
            ItemStack removed = removeItem(i);
            if (removed != null && !removed.isEmpty()) {
                SyncBlockItemMessage message = new SyncBlockItemMessage(getBlockPos(), ItemStack.EMPTY, i);
                QuestownNetwork.CHANNEL.send(PacketDistributor.ALL.noArg(), message);
                return removed;
            }
        }
        return ItemStack.EMPTY;
    }

    private ItemStack removeItem(int index) {
        ItemStack itemStack = items.get(index);
        if (itemStack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        items.set(index, ItemStack.EMPTY);
        return itemStack;
    }

    @Override
    public boolean setItem(
            ItemStack item,
            int index
    ) {
        if (item.isEmpty()) {
            if (items.get(index).isEmpty()) {
                return false;
            }
            items.set(index, item);
            return true;
        }
        if (items.get(index).isEmpty()) {
            items.set(index, item);
            return true;
        }
        return false;
    }
}

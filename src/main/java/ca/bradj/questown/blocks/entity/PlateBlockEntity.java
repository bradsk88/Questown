package ca.bradj.questown.blocks.entity;

import ca.bradj.questown.core.init.TilesInit;
import ca.bradj.questown.core.network.QuestownNetwork;
import ca.bradj.questown.core.network.SyncBlockItemMessage;
import ca.bradj.questown.integration.minecraft.MCTownItem;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.network.PacketDistributor;

public class PlateBlockEntity extends BlockEntity implements ItemAccepting<MCTownItem> {
    private ItemStack food = Items.AIR.getDefaultInstance();

    public PlateBlockEntity(
            BlockPos p_155229_,
            BlockState p_155230_
    ) {
        super(TilesInit.PLATE.get(), p_155229_, p_155230_);
    }

    public ItemStack getFood() {
        return this.food;
    }


    @Override
    public boolean setItem(
            int index,
            MCTownItem item
    ) {
        this.food = item.toItemStack();
        if (level.isClientSide()) {
            return false;
        }
        QuestownNetwork.CHANNEL.send(
                PacketDistributor.ALL.noArg(), new SyncBlockItemMessage(
                        getBlockPos(), this.food, index
                )
        );
        return true;
    }

    public void setFood(ItemStack itemStack) {
        setItem(0, MCTownItem.fromMCItemStack(itemStack));
    }
}

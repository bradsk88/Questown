package ca.bradj.questown.blocks.entity;

import ca.bradj.questown.core.init.TagsInit;
import ca.bradj.questown.core.init.TilesInit;
import ca.bradj.questown.integration.minecraft.MCContainerInterface;
import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.integration.minecraft.MCTownItem;
import ca.bradj.questown.jobs.leaver.RankBoost;
import ca.bradj.questown.mc.Compat;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AxeRackBlockEntity extends BlockEntity implements MCContainerInterface {
    private @NotNull MCTownItem item = MCTownItem.Air();

    public AxeRackBlockEntity(
            BlockPos blockPos,
            BlockState blockState
    ) {
        super(TilesInit.AXE_RACK.get(), blockPos, blockState);
    }

    @Override
    public int size() {
        return 1;
    }

    @Override
    public MCTownItem getItem(int i) {
        return item;
    }

    @Override
    public MCTownItem removeItem(int index) {
        @Nullable MCTownItem old = item;
        setItem(index, MCTownItem.Air());
        return old;
    }

    @Override
    public boolean isFull() {
        return !MCTownItem.Air().equals(item);
    }

    @Override
    public String toShortString() {
        return "AxeRack[" + item.getShortName() + "]";
    }

    @Override
    public String toShortString(boolean includeAir) {
        return toShortString();
    }

    @Override
    public boolean canAcceptIfSpaceAllows(MCTownItem item) {
        return Ingredient.of(TagsInit.Items.AXES).test(item.toMCItemStack());
    }

    @Override
    public RankBoost getItemAcceptanceRankBoost() {
        return RankBoost.VERY_PREFERRED;
    }

    @Override
    public boolean setItem(
            int index,
            MCTownItem item
    ) {
        if (this.item.isEmpty()) {
            this.item = item;
            if (level instanceof  ServerLevel sl) {
                Compat.playSound(sl, getBlockPos(), SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.AMBIENT);
            }
            return true;
        }
        if (item.isEmpty()) {
            this.item = MCTownItem.Air();
            if (level instanceof  ServerLevel sl) {
                Compat.playSound(sl, getBlockPos(), SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.AMBIENT);
            }
            return true;
        }
        return false;
    }

    public MCTownItem getItem() {
        return getItem(0);
    }

    public void addItem(MCHeldItem item) {
        if (this.item.isEmpty()) {
            setItem(0, item.toItem());
        }
    }
}

package ca.bradj.questown.blocks.entity;

import ca.bradj.questown.blocks.HospitalBedBlock;
import ca.bradj.questown.core.init.TilesInit;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class HospitalBedBlockEntity extends BlockEntity {
    private DyeColor color;

    public HospitalBedBlockEntity(BlockPos p_155115_, BlockState p_155116_) {
        this(TilesInit.HOSPITAL_BED.get(), p_155115_, p_155116_, ((HospitalBedBlock)p_155116_.getBlock()).getColor());
    }

    private HospitalBedBlockEntity(BlockEntityType<HospitalBedBlockEntity> t, BlockPos p_155118_, BlockState p_155119_, DyeColor p_155120_) {
        super(t, p_155118_, p_155119_);
        this.color = p_155120_;
    }

    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    public DyeColor getColor() {
        return this.color;
    }

    public void setColor(DyeColor p_58730_) {
        this.color = p_58730_;
    }
}
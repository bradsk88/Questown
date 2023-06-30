package ca.bradj.questown.jobs;

import ca.bradj.questown.Questown;
import ca.bradj.questown.blocks.GathererDummyBlock;
import ca.bradj.questown.core.init.TilesInit;
import ca.bradj.questown.integration.minecraft.GathererStatuses;
import ca.bradj.questown.integration.minecraft.MCTownInventory;
import ca.bradj.questown.integration.minecraft.MCTownItem;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

public class GathererEntity extends BlockEntity implements GathererJournal.SignalSource {
    public static final String ID = "gatherer_block_entity";

    private final GathererJournal<MCTownInventory, MCTownItem> journal = new GathererJournal<>(
            this, () -> new MCTownItem(Items.AIR)
    ) {
        @Override
        protected void changeStatus(Statuses s) {
            super.changeStatus(s);
            getBlockState().setValue(GathererDummyBlock.STATUS, GathererStatuses.fromQT(s));
            Questown.LOGGER.debug("Changed status to {}", s);
        }
    };
    private GathererJournal.Signals signal;

    public GathererEntity(BlockPos p_155229_, BlockState p_155230_) {
        super(TilesInit.GATHERER.get(), p_155229_, p_155230_);
    }


    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag updateTag = super.getUpdateTag();
        updateTag.putString("status", journal.getStatus().name());
        return updateTag;
    }

    private void updateStatusFromTag(CompoundTag nbt) {
        String rawStatus = nbt.getString("status");
        GathererStatuses status = GathererStatuses.fromString(rawStatus);
        journal.changeStatus(status.toQT());
    }

    @Override
    public void onLoad() {
        this.initializeStatus(getBlockState());
        super.onLoad();
    }

    public static void tick(
            Level level, BlockPos blockPos, BlockState blockState, GathererEntity e
    ) {
        // TODO: This isn't syncing back to the client side, it seems.
        blockState.setValue(
                GathererDummyBlock.STATUS,
                GathererStatuses.fromQT(e.journal.getStatus())
        );
    }

    @Override
    public GathererJournal.Signals getSignal() {
        return this.signal;
    }

    public void initializeStatus(BlockState state) {
        GathererStatuses status = state.getValue(GathererDummyBlock.STATUS);
        GathererJournal.Statuses value = status.toQT();
        Questown.LOGGER.debug("Initialized journal to state {}", value);
        this.journal.initializeStatus(value);
    }
}

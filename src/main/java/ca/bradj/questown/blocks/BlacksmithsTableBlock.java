package ca.bradj.questown.blocks;

import ca.bradj.questown.core.init.items.ItemsInit;
import com.google.common.collect.ImmutableList;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Material;
import net.minecraft.world.level.material.MaterialColor;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.phys.BlockHitResult;

import java.util.List;

public class BlacksmithsTableBlock extends Block {
    public static final String ITEM_ID = "blacksmiths_table";

    public BlacksmithsTableBlock(
    ) {
        super(
                Properties
                        .of(Material.WOOL, MaterialColor.COLOR_BROWN)
                        .strength(1.0F, 10.0F)
        );
    }

    @Override
    public List<ItemStack> getDrops(
            BlockState p_60537_,
            LootContext.Builder p_60538_
    ) {
        // TODO: Also drop stuff inside
        //  Maybe something like onHurt() { town.dropInsertedItems(this.pos) }
        //  So the first hit from a player will cause the items to pop out.
        return ImmutableList.of(ItemsInit.ORE_PROCESSING_BLOCK.get().getDefaultInstance());
    }

    @Override
    public InteractionResult use(
            BlockState blockState,
            Level level,
            BlockPos pos,
            Player player,
            InteractionHand p_60507_,
            BlockHitResult p_60508_
    ) {
        if (!(level instanceof ServerLevel sl)) {
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

//        if (Integer.valueOf(BlacksmithWoodenPickaxeJob.MAX_STATE).equals(JobBlock.getState(sl, pos))) {
//            moveOreToWorld(sl, pos, is -> player.getInventory().add(is));
//            return InteractionResult.sidedSuccess(level.isClientSide);
//        }

        // TODO: Generic handling

        return InteractionResult.PASS;
    }
}

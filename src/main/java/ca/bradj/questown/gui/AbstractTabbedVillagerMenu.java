package ca.bradj.questown.gui;

import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public abstract class AbstractTabbedVillagerMenu extends AbstractVillagerMenu implements VillagerTabsEmbedding {

    protected AbstractTabbedVillagerMenu(
            @Nullable MenuType<?> p_38851_,
            @Nullable Container villagerInventory,
            @Nullable Inventory playerInventory,
            int windowId,
            BlockPos flagPos,
            UUID villagerUUID
    ) {
        super(p_38851_, villagerInventory, playerInventory, windowId, flagPos, villagerUUID);
    }

}

package ca.bradj.questown.gui;

import net.minecraft.core.BlockPos;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public abstract class AbstractVillagerMenu extends AbstractContainerMenu implements VillagerTabsEmbedding {
    protected final BlockPos flagPos;
    protected final UUID villagerUUID;

    protected AbstractVillagerMenu(
            @Nullable MenuType<?> p_38851_, int windowId, BlockPos flagPos, UUID villagerUUID
    ) {
        super(p_38851_, windowId);
        this.flagPos = flagPos;
        this.villagerUUID = villagerUUID;
    }

    @Override
    public BlockPos getFlagPos() {
        return flagPos;
    }

    @Override
    public UUID getVillagerUUID() {
        return villagerUUID;
    }
}

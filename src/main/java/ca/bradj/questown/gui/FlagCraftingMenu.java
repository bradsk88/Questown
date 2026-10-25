package ca.bradj.questown.gui;

import net.minecraft.resources.ResourceLocation;

import com.google.common.collect.ImmutableList;

import ca.bradj.questown.core.init.MenuTypesInit;
import ca.bradj.questown.core.network.OpenFlagMenuMessage;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

import java.util.Collection;

public class FlagCraftingMenu extends AbstractContainerMenu implements FlagTabsEmbedding {
    private static final Collection<String> ENABLED_TABS = FlagTabs.allExcept(OpenFlagMenuMessage.CRAFTING);
    private final FlagInfo flagInfo;

    public static FlagCraftingMenu ForClientSide(
            int windowId,
            Inventory inv,
            FriendlyByteBuf buf
    ) {
        FlagMenus menus = FlagMenus.fromNetwork(windowId, inv.player, buf);
        return menus.craftingMenu;
    }

    /** Ids of the town's posted quests (open or completed): which gated rows show (r17 #1). */
    private final ImmutableList<ResourceLocation> postedQuests;

    public FlagCraftingMenu(int windowId, FlagInfo flagInfo) {
        this(windowId, flagInfo, ImmutableList.of());
    }

    public FlagCraftingMenu(int windowId, FlagInfo flagInfo, Collection<ResourceLocation> postedQuests) {
        super(MenuTypesInit.FLAG_CRAFTING.get(), windowId);
        this.flagInfo = flagInfo;
        this.postedQuests = ImmutableList.copyOf(postedQuests);
    }

    public ImmutableList<ResourceLocation> getPostedQuests() {
        return postedQuests;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int i) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public Collection<String> getEnabledTabs() {
        return ENABLED_TABS;
    }

    @Override
    public FlagInfo getFlagInfo() {
        return flagInfo;
    }
}

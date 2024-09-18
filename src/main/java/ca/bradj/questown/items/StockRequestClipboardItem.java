package ca.bradj.questown.items;

import ca.bradj.questown.Questown;
import ca.bradj.questown.blocks.TownFlagBlock;
import ca.bradj.questown.core.network.OnScreenTextMessage;
import ca.bradj.questown.core.network.QuestownNetwork;
import ca.bradj.questown.gui.CreateStockRequestContainer;
import ca.bradj.questown.jobs.JobsRegistry;
import ca.bradj.questown.jobs.WorksBehaviour;
import ca.bradj.questown.town.TownFlagBlockEntity;
import com.google.common.collect.ImmutableSet;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkHooks;
import net.minecraftforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;

public class StockRequestClipboardItem extends Item {
    public static final String ITEM_ID = "stock_request_clipboard";

    public StockRequestClipboardItem() {
        super(Questown.DEFAULT_ITEM_PROPS);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level lvl, Player player, InteractionHand hand) {
        if (!(player instanceof ServerPlayer sp)) {
            return super.use(lvl, player, hand);
        }

        ItemStack itemInHand = player.getItemInHand(hand);
        TownFlagBlockEntity parent = TownFlagBlock.GetParentFromNBT(sp.getLevel(), itemInHand);
        if (parent == null) {
            OnScreenTextMessage msg = new OnScreenTextMessage("message.flag_item.no_flag");
            QuestownNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sp), msg);
            return InteractionResultHolder.fail(itemInHand);
        }

        WorksBehaviour.TownData td = parent.getTownData();
        BlockPos flagPos = parent.getTownFlagBasePos();
        ImmutableSet<Ingredient> allOutputs = JobsRegistry.getAllOutputs(td);
        NetworkHooks.openGui(sp, new MenuProvider() {
            @Override
            public @NotNull Component getDisplayName() {
                return TextComponent.EMPTY;
            }

            @Override
            public @NotNull AbstractContainerMenu createMenu(
                    int windowId,
                    @NotNull Inventory inv,
                    @NotNull Player p
            ) {
                return new CreateStockRequestContainer(windowId, allOutputs, flagPos);
            }
        }, data -> {
            CreateStockRequestContainer.writeWorkResults(allOutputs, data);
            CreateStockRequestContainer.writeFlagPosition(flagPos, data);
        });
        return InteractionResultHolder.sidedSuccess(itemInHand, false);
    }
}

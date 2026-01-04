package ca.bradj.questown.gui;

import ca.bradj.questown.core.init.MenuTypesInit;
import ca.bradj.questown.core.network.*;
import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.jobs.*;
import ca.bradj.questown.jobs.production.ProductionStatus;
import ca.bradj.questown.mobs.visitor.VisitorMobEntity;
import com.google.common.collect.ImmutableList;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;
import java.util.Stack;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;

public class InventoryAndStatusMenu extends AbstractTabbedVillagerMenu implements StatusListener {

    private static final Collection<String> ENABLED_TABS = VillagerTabs.except(OpenVillagerMenuMessage.INVENTORY);
    private static final int boxHeight = 18;
    private final DataSlot statusSlot;
    final JobID jobId;

    private final Stack<Runnable> closers = new Stack<>();
    private final boolean showBlockOfProgressTab;

    public static InventoryAndStatusMenu ForClientSide(
            int windowId,
            Inventory inv,
            FriendlyByteBuf buf
    ) {
        VillagerMenus menus = VillagerMenus.fromNetwork(windowId, inv.player, buf);
        return menus.invMenu;
    }


    public <S extends IStatus<S>> InventoryAndStatusMenu(
            int windowId,
            Container gathererInv,
            Inventory inv,
            Collection<Boolean> slotLocks,
            UUID villagerUUID,
            JobID jobId,
            BlockPos flagPos,
            boolean showBlockOfProgressTab
    ) {
        super(MenuTypesInit.GATHERER_INVENTORY.get(), gathererInv, inv, windowId, flagPos, villagerUUID);
        this.jobId = jobId;

        layoutSlots(gathererInv);
        this.addDataSlot(this.statusSlot = DataSlot.standalone());
        this.showBlockOfProgressTab = showBlockOfProgressTab;
    }

    public boolean stillValid(Player p_38874_) {
        // TODO: Consider checking distance
        return true;
    }

    public IStatus<?> getStatus() {
        return SessionUniqueOrdinals.getStatus(this.statusSlot.get());
    }

    @Override
    public void statusChanged(IStatus<?> newStatus) {
        this.statusSlot.set(SessionUniqueOrdinals.getOrdinal(newStatus));
        if (!(getPlayer() instanceof ServerPlayer sp)) {
            return;
        }
        ResourceLocation tex = ServerJobsRegistry.getTexture(jobId, newStatus);
        @NotNull ImmutableList<Component> text = ServerJobsRegistry.getStatusText(jobId, newStatus);
        if (newStatus instanceof ProductionStatus ps) {
            QuestownNetwork.CHANNEL.send(
                    PacketDistributor.PLAYER.with(() -> sp),
                    new SyncStatusArtMessage(jobId, ps, tex)
            );
            if (text.isEmpty()) {
                return;
            }
            QuestownNetwork.CHANNEL.send(
                    PacketDistributor.PLAYER.with(() -> sp),
                    new SyncStatusTextMessage(ps, new StatusPacket(jobId, text, tex))
            );
        }

    }

    public String getRootJobId() {
        return this.jobId.rootId();
    }

    @Override
    public void onClose() {
        closers.forEach(Runnable::run);
    }

    @Override
    public Collection<String> getEnabledTabs() {
        return ENABLED_TABS;
    }

    @Override
    public boolean showBlockOfProgressTab() {
        return showBlockOfProgressTab;
    }

    public void connectToServer(
            VisitorMobEntity e,
            ServerPlayer sender
    ) {
        IStatus<?> status = e.getStatusForServer();
        addStatusListener(e, status);
        addWantedIngredientsListener(e, sender);
        statusChanged(status);
    }

    @Override
    public Runnable jobChanged(Function<StatusListener, Runnable> listenToNewJob) {
        return listenToNewJob.apply(this);
    }

    private void addStatusListener(
            VisitorMobEntity e,
            IStatus<?> status
    ) {
        this.statusSlot.set(SessionUniqueOrdinals.getOrdinal(status));
        e.addStatusListener(this);
        this.closers.add(() -> e.removeStatusListener(this));
    }

    private void addWantedIngredientsListener(
            VisitorMobEntity e,
            ServerPlayer sender
    ) {
        JobWantedIngredientsMessage msg = buildMessage(e);
        PacketDistributor.PacketTarget tgt = PacketDistributor.PLAYER.with(() -> sender);
        Consumer<ImmutableList<Ingredient>> listener = ingr -> QuestownNetwork.CHANNEL.send(tgt, msg);

        e.addWantedIngredientsListener(listener);
        this.closers.add(() -> e.removeWantedIngredientsListener(listener));
        QuestownNetwork.CHANNEL.send(tgt, buildMessage(e));
    }

    private static @NotNull JobWantedIngredientsMessage buildMessage(
            VisitorMobEntity e
    ) {
        Function<List<MCHeldItem>, ImmutableList<Ingredient>> wantFn = ServerJobsRegistry.getWantedResourcesProvider(e.getJobId());
        return new JobWantedIngredientsMessage(wantFn.apply(Jobs.getHeldItems(e.getInventory())));
    }
}

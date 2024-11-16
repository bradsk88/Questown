package ca.bradj.questown.gui;

import ca.bradj.questown.core.init.MenuTypesInit;
import ca.bradj.questown.core.network.JobWantedIngredientsMessage;
import ca.bradj.questown.core.network.OpenVillagerMenuMessage;
import ca.bradj.questown.core.network.QuestownNetwork;
import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.jobs.*;
import ca.bradj.questown.mobs.visitor.VisitorMobEntity;
import com.google.common.collect.ImmutableList;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;

public class MultiStatusMenu extends AbstractContainerMenu {

    private static final Collection<String> ENABLED_TABS = ImmutableList.of(
            OpenVillagerMenuMessage.QUESTS,
            OpenVillagerMenuMessage.STATS,
            OpenVillagerMenuMessage.SKILLS
    );

    private static final int inventoryLeftX = 8;
    private static final int boxHeight = 18, boxWidth = 18;
    private static final int margin = 4;
    private final Map<UUID, DataSlot> statusSlots = new HashMap<>();
    final List<DataSlot> lockedSlots = new ArrayList<>(
    );
    final Map<UUID, JobID> jobIds;

    private final Stack<Runnable> closers = new Stack<>();

    public static MultiStatusMenu ForClientSide(
            int windowId,
            Inventory inv,
            FriendlyByteBuf buf
    ) {
        Function<FriendlyByteBuf, UUID> uidRd = FriendlyByteBuf::readUUID;
        HashMap<UUID, JobID> jobs = buf.readMap(HashMap::new, uidRd, Jobs::getIdFromNetwork);
        return new MultiStatusMenu(windowId, jobs);
    }

    public static void toNetwork(
            FriendlyByteBuf buf,
            Map<UUID, JobID> jobIds
    ) {
        buf.writeMap(jobIds, FriendlyByteBuf::writeUUID, Jobs::writeIdToNetwork);
    }

    public <S extends IStatus<S>> MultiStatusMenu(
            int windowId,
            Map<UUID, JobID> jobIds
    ) {
        super(MenuTypesInit.MULTI_VILLAGER.get(), windowId);
        this.jobIds = jobIds;

        jobIds.keySet().forEach(uuid -> {
            DataSlot slot = this.addDataSlot(DataSlot.standalone());
            this.statusSlots.put(uuid, slot);
        });
    }

    public boolean stillValid(Player p_38874_) {
        // TODO: Consider checking distance
        return true;
    }

    @Override
    protected boolean moveItemStackTo(
            ItemStack p_38904_,
            int p_38905_,
            int p_38906_,
            boolean p_38907_
    ) {
        return false;
    }

    public IStatus<?> getStatus(UUID villagerUUID) {
        DataSlot dataSlot = this.statusSlots.get(villagerUUID);
        return SessionUniqueOrdinals.getStatus(dataSlot.get());
    }

    public void onClose() {
        closers.forEach(Runnable::run);
    }

    public void connectToServer(
            Collection<VisitorMobEntity> es,
            ServerPlayer sender
    ) {
        es.forEach(e -> {
            IStatus<?> status = e.getStatusForServer();
            addStatusListener(e, status);
            addWantedIngredientsListener(e, sender);
        });
    }

    private void addStatusListener(
            VisitorMobEntity e,
            IStatus<?> status
    ) {
        DataSlot slot = statusSlots.get(e.getUUID());
        slot.set(SessionUniqueOrdinals.getOrdinal(status));
        StatusListener sl = newStatus -> slot.set(SessionUniqueOrdinals.getOrdinal(newStatus));
        e.addStatusListener(sl);
        this.closers.add(() -> e.removeStatusListener(sl));
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
        Function<List<MCHeldItem>, ImmutableList<Ingredient>> wantFn = JobsRegistry.getWantedResourcesProvider(e.getJobId());
        return new JobWantedIngredientsMessage(wantFn.apply(Jobs.getHeldItems(e.getInventory())));
    }

    public Iterable<UUID> getVillagerUUIDs() {
        return statusSlots.keySet();
    }

    public JobID getJobId(UUID uuid) {
        return jobIds.get(uuid);
    }
}

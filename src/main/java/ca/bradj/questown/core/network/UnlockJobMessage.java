package ca.bradj.questown.core.network;

import ca.bradj.questown.QT;
import ca.bradj.questown.core.init.TilesInit;
import ca.bradj.questown.gui.BopTransactionSyncer;
import ca.bradj.questown.gui.JobUnlockConfirmMenu;
import ca.bradj.questown.jobs.JobID;
import ca.bradj.questown.jobs.Jobs;
import ca.bradj.questown.mc.Compat;
import ca.bradj.questown.town.TownFlagBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraftforge.network.NetworkEvent;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

public record UnlockJobMessage(BlockPos flagPos, UUID villagerUUID, JobID id, boolean confirmFirst) {

    public static void encode(
            UnlockJobMessage msg,
            FriendlyByteBuf buffer
    ) {
        buffer.writeBlockPos(msg.flagPos);
        buffer.writeUUID(msg.villagerUUID);
        Jobs.writeIdToNetwork(buffer, msg.id);
        buffer.writeBoolean(msg.confirmFirst);
    }

    public static UnlockJobMessage decode(FriendlyByteBuf buffer) {
        BlockPos flagPos = buffer.readBlockPos();
        UUID uuid = buffer.readUUID();
        JobID jobId = Jobs.getIdFromNetwork(buffer);
        return new UnlockJobMessage(flagPos, uuid, jobId, buffer.readBoolean());
    }

    public void handle(
            Supplier<NetworkEvent.Context> ctx
    ) {
        ctx.get().enqueueWork(() -> {
            // Work that needs to be thread-safe (most work)
            ServerPlayer sender = ctx.get().getSender(); // the client that sent this packet
            // Do stuff
            Optional<TownFlagBlockEntity> flag = sender.getLevel().getBlockEntity(flagPos, TilesInit.TOWN_FLAG.get());
            if (flag.isEmpty()) {
                QT.GUI_LOGGER.error("No flag at position {}. Quest will not be removed.", flagPos);
                return;
            }
            if (confirmFirst) {
                Compat.openScreen(
                        sender, new MenuProvider() {
                            @Override
                            public @Nullable AbstractContainerMenu createMenu(
                                    int i,
                                    Inventory inventory,
                                    Player player
                            ) {
                                return new JobUnlockConfirmMenu(
                                        i, new SimpleContainer(1) {
                                    @Override
                                    public int getMaxStackSize() {
                                        return 1;
                                    }
                                }, inventory, villagerUUID, id, flagPos
                                );
                            }

                            @Override
                            public Component getDisplayName() {
                                return Compat.literal("");
                            }
                        }, data -> JobUnlockConfirmMenu.ToNetwork(data, villagerUUID, id, flagPos)
                );
                return;
            }

            BopTransactionSyncer.syncConsumedBOP(sender);
            unlockJob(flag, sender);
        });
        ctx.get().setPacketHandled(true);

    }

    private void unlockJob(
            Optional<TownFlagBlockEntity> flag,
            ServerPlayer sender
    ) {
        flag.get().getVillagerHandle().unlockJob(villagerUUID, id);
        String key = "messages.jobs.unlocked_by_mod";
        MutableComponent msg = Compat.translatable(key, sender.getName(), id.toNiceString(), villagerUUID);
        for (Player player : sender.level.players()) {
            Compat.sendMessage((ServerPlayer) player, msg);
        }
    }
}



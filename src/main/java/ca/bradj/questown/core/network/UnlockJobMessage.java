package ca.bradj.questown.core.network;

import ca.bradj.questown.QT;
import ca.bradj.questown.core.advancements.TutorialTrigger;
import ca.bradj.questown.core.init.AdvancementsInit;
import ca.bradj.questown.core.init.TilesInit;
import ca.bradj.questown.gui.BopTransactionSyncer;
import ca.bradj.questown.gui.JobUnlockConfirmMenu;
import ca.bradj.questown.jobs.JobID;
import ca.bradj.questown.jobs.Jobs;
import ca.bradj.questown.mc.Compat;
import ca.bradj.questown.town.entity.TownFlagBlockEntity;
import net.minecraft.core.BlockPos;
import ca.bradj.questown.loader.Channel;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

public record UnlockJobMessage(BlockPos flagPos, UUID villagerUUID, JobID id, boolean confirmFirst) {

    public static void encode(
            UnlockJobMessage msg,
            RegistryFriendlyByteBuf buffer
    ) {
        buffer.writeBlockPos(msg.flagPos);
        buffer.writeUUID(msg.villagerUUID);
        Jobs.writeIdToNetwork(buffer, msg.id);
        buffer.writeBoolean(msg.confirmFirst);
    }

    public static UnlockJobMessage decode(RegistryFriendlyByteBuf buffer) {
        BlockPos flagPos = buffer.readBlockPos();
        UUID uuid = buffer.readUUID();
        JobID jobId = Jobs.getIdFromNetwork(buffer);
        return new UnlockJobMessage(flagPos, uuid, jobId, buffer.readBoolean());
    }

    public void handle(
            Channel.Context ctx
    ) {
        ctx.enqueueWork(() -> {
            // Work that needs to be thread-safe (most work)
            ServerPlayer sender = (ServerPlayer) ctx.sender(); // the client that sent this packet
            // Do stuff
            Optional<TownFlagBlockEntity> flag = Optional.ofNullable(sender.level().getBlockEntity(flagPos)).filter(TownFlagBlockEntity.class::isInstance).map(TownFlagBlockEntity.class::cast);
            if (flag.isEmpty()) {
                QT.GUI_LOGGER.error("No flag at position {}. Quest will not be removed.", flagPos);
                return;
            }
            // Level gate (skills-tab ruling): a job whose JSON available_at_level is above the
            // townie's level is not yet unlockable. The skills tab hides it, but a stale client
            // (or a direct packet) could still name it - reject server-side.
            int level = flag.get().getVillagerHandle().getVillagerLevel(villagerUUID);
            int availableAtLevel = ca.bradj.questown.jobs.ServerJobsRegistry.getAvailableAtLevel(id);
            if (availableAtLevel > level) {
                QT.GUI_LOGGER.info(
                        "Refusing unlock of {} for {} at level {}: the job's available_at_level "
                                + "({}) is not reached yet",
                        id,
                        villagerUUID,
                        level,
                        availableAtLevel
                );
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
            AdvancementsInit.TUTORIAL_TRIGGER.trigger(sender, TutorialTrigger.Triggers.FirstBopSpend);
            unlockJob(flag, sender);
        });

    }

    private void unlockJob(
            Optional<TownFlagBlockEntity> flag,
            ServerPlayer sender
    ) {
        flag.get().getVillagerHandle().unlockJob(villagerUUID, id);
        String key = "messages.jobs.unlocked_by_mod";
        // The villager by name, not the raw UUID: 1.20.6 rejects a UUID translation argument
        // (2026-10-20 playtest: "TranslatableContents' arguments must be ...", unlock unsent).
        String villager = flag.get().getVillagerHandle().labelFor(villagerUUID);
        MutableComponent msg = Compat.translatable(key, sender.getName(), id.toNiceString(), villager);
        for (Player player : sender.serverLevel().players()) {
            Compat.sendMessage((ServerPlayer) player, msg);
        }
    }
}



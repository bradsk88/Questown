package ca.bradj.questown.core.network;

import ca.bradj.questown.QT;
import ca.bradj.questown.core.advancements.VisitorTrigger;
import ca.bradj.questown.core.init.AdvancementsInit;
import ca.bradj.questown.jobs.ServerJobsRegistry;
import ca.bradj.questown.jobs.WorksBehaviour;
import ca.bradj.questown.town.TownFlagBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;

public record AddWorkFromUIMessage(
        ItemStack requested,
        int flagX, int flagY, int flagZ,
        Action action
) {

    public enum Action {
        INQUIRED,
        CONFIRMED,
        REJECTED,
    }

    public static void encode(
            AddWorkFromUIMessage msg,
            FriendlyByteBuf buffer
    ) {
        buffer.writeItem(msg.requested);
        buffer.writeInt(msg.flagX);
        buffer.writeInt(msg.flagY);
        buffer.writeInt(msg.flagZ);
        buffer.writeInt(msg.action().ordinal());
    }

    public static AddWorkFromUIMessage decode(FriendlyByteBuf buffer) {
        ItemStack requested = buffer.readItem();
        int flagX = buffer.readInt();
        int flagY = buffer.readInt();
        int flagZ = buffer.readInt();
        Action confirmed = Action.values()[buffer.readInt()];
        return new AddWorkFromUIMessage(requested, flagX, flagY, flagZ, confirmed);
    }


    public void handle(
            Supplier<NetworkEvent.Context> ctx
    ) {
        ctx.get().enqueueWork(() -> {
            // Work that needs to be thread-safe (most work)
            ServerPlayer sender = ctx.get().getSender(); // the client that sent this packet
            // Do stuff
            BlockEntity flag = sender.getLevel()
                                     .getBlockEntity(new BlockPos(flagX, flagY, flagZ));
            if (!(flag instanceof TownFlagBlockEntity tfbe)) {
                QT.GUI_LOGGER.error("No flag at position {}, {}, {}. Work will not be added.", flagX, flagY, flagZ);
                return;
            }
            switch (action) {
                case INQUIRED -> {
                    WorksBehaviour.TownData td = tfbe.getTownData();
                    Ingredient result = Ingredient.of(requested.getItem());
                    QuestownNetwork.CHANNEL.send(
                            PacketDistributor.PLAYER.with(() -> sender),
                            new WorkRequestConfirmMessage(
                                    requested().getItem().getRegistryName(),
                                    ServerJobsRegistry.getAllJobsThatProduce(td, result),
                                    tfbe.getTownFlagBasePos()
                            )
                    );
                }
                case CONFIRMED -> {
                    VisitorTrigger.Triggers adv = VisitorTrigger.Triggers.FirstJobRequest;
                    AdvancementsInit.VISITOR_TRIGGER.trigger(ctx.get().getSender(), adv);
                    tfbe.getWorkHandle().requestWork(requested.getItem());
                    tfbe.openJobsMenu(sender, false);
                }
                case REJECTED -> tfbe.openJobsMenu(sender, true);
            }
        });
        ctx.get().setPacketHandled(true);

    }
}

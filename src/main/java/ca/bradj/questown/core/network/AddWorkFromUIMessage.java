package ca.bradj.questown.core.network;

import ca.bradj.questown.QT;
import ca.bradj.questown.core.advancements.VisitorTrigger;
import ca.bradj.questown.core.init.AdvancementsInit;
import ca.bradj.questown.gui.Ingredients;
import ca.bradj.questown.jobs.ServerJobsRegistry;
import ca.bradj.questown.jobs.WorksBehaviour;
import ca.bradj.questown.town.TownFlagBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.Objects;
import java.util.function.Supplier;

/**
 */
public final class AddWorkFromUIMessage {
    private final Ingredient requested;
    private final BlockPos flagPos;
    private final Action action;

    public AddWorkFromUIMessage(
            Ingredient requested,
            BlockPos flagPos,
            Action action
    ) {
        this.requested = requested;
        this.flagPos = flagPos;
        this.action = action;
    }

    public enum Action {
        INQUIRED,
        CONFIRMED,
        REJECTED,
    }

    public static void encode(
            AddWorkFromUIMessage msg,
            FriendlyByteBuf buffer
    ) {
        buffer.writeInt(msg.action().ordinal());
        Ingredients.toNetwork(msg.requested, buffer);
        buffer.writeBlockPos(msg.flagPos);
    }

    public static AddWorkFromUIMessage decode(FriendlyByteBuf buffer) {
        Action action = Action.values()[buffer.readInt()];
        Ingredient request = Ingredients.fromNetwork(buffer);
        BlockPos flagPos = buffer.readBlockPos();
        return new AddWorkFromUIMessage(request, flagPos, action);
    }


    public void handle(
            Supplier<NetworkEvent.Context> ctx
    ) {
        ctx.get().enqueueWork(() -> {
            // Work that needs to be thread-safe (most work)
            ServerPlayer sender = ctx.get().getSender(); // the client that sent this packet
            // Do stuff
            BlockEntity flag = sender.getLevel().getBlockEntity(flagPos);
            if (!(flag instanceof TownFlagBlockEntity tfbe)) {
                QT.GUI_LOGGER.error("No flag at position {}. Work will not be added.", flagPos);
                return;
            }
            switch (action) {
                case INQUIRED -> {
                    WorksBehaviour.TownData td = tfbe.getTownData();
                    Ingredient result = (Ingredient) requested;
                    QuestownNetwork.CHANNEL.send(
                            PacketDistributor.PLAYER.with(() -> sender),
                            new WorkRequestConfirmMessage(
                                    (Ingredient) requested(),
                                    ServerJobsRegistry.getAllJobsThatProduce(td, result),
                                    tfbe.getTownFlagBasePos()
                            )
                    );
                }
                case CONFIRMED -> {
                    VisitorTrigger.Triggers adv = VisitorTrigger.Triggers.FirstJobRequest;
                    AdvancementsInit.VISITOR_TRIGGER.trigger(ctx.get().getSender(), adv);
                    tfbe.getWorkHandle().requestWork(Ingredients.asWorkRequest(requested));
                    tfbe.openJobsMenu(sender, false);
                }
                case REJECTED -> tfbe.openJobsMenu(sender, true);
            }
        });
        ctx.get().setPacketHandled(true);

    }

    public Object requested() {
        return requested;
    }

    public BlockPos flagPos() {
        return flagPos;
    }

    public Action action() {
        return action;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (AddWorkFromUIMessage) obj;
        return Objects.equals(this.requested, that.requested) &&
                Objects.equals(this.flagPos, that.flagPos) &&
                Objects.equals(this.action, that.action);
    }

    @Override
    public int hashCode() {
        return Objects.hash(requested, flagPos, action);
    }

    @Override
    public String toString() {
        return "AddWorkFromUIMessage[" +
                "requested=" + requested + ", " +
                "flagPos=" + flagPos + ", " +
                "action=" + action + ']';
    }

}

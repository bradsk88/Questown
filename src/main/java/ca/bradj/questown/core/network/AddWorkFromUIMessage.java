package ca.bradj.questown.core.network;

import ca.bradj.questown.QT;
import ca.bradj.questown.core.advancements.VisitorTrigger;
import ca.bradj.questown.core.init.AdvancementsInit;
import ca.bradj.questown.gui.Ingredients;
import ca.bradj.questown.jobs.JobID;
import ca.bradj.questown.jobs.ServerJobsRegistry;
import ca.bradj.questown.jobs.WorksBehaviour;
import ca.bradj.questown.town.entity.TownFlagBlockEntity;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import ca.bradj.questown.loader.Channel;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.Objects;

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

    public static boolean canEncode(Ingredient item) {
        try {
            Ingredients.toString(item);
            return true;
        } catch (Exception e) {
            QT.GUI_LOGGER.error("Failed to encode ingredient {}: {}", item, e.getMessage());
            return false;
        }
    }

    public enum Action {
        INQUIRED,
        CONFIRMED,
        REJECTED,
    }

    public static void encode(
            AddWorkFromUIMessage msg,
            RegistryFriendlyByteBuf buffer
    ) {
        buffer.writeInt(msg.action().ordinal());
        Ingredients.toNetwork(msg.requested, buffer);
        buffer.writeBlockPos(msg.flagPos);
    }

    public static AddWorkFromUIMessage decode(RegistryFriendlyByteBuf buffer) {
        Action action = Action.values()[buffer.readInt()];
        Ingredient request = Ingredients.fromNetwork(buffer);
        BlockPos flagPos = buffer.readBlockPos();
        return new AddWorkFromUIMessage(request, flagPos, action);
    }


    public void handle(
            Channel.Context ctx
    ) {
        ctx.enqueueWork(() -> {
            // Work that needs to be thread-safe (most work)
            ServerPlayer sender = (ServerPlayer) ctx.sender(); // the client that sent this packet
            // Do stuff
            BlockEntity flag = sender.level().getBlockEntity(flagPos);
            if (!(flag instanceof TownFlagBlockEntity tfbe)) {
                QT.GUI_LOGGER.error("No flag at position {}. Work will not be added.", flagPos);
                return;
            }
            switch (action) {
                case INQUIRED -> {
                    WorksBehaviour.TownData td = tfbe.getTownData();
                    Ingredient result = (Ingredient) requested;
                    // Only jobs the town KNOWS count - progressive discovery means
                    // "20 known jobs" should not count the whole dataset
                    // (2026-10-15 playtest: the hint listed jobs the town has
                    // never been revealed).
                    ImmutableSet<JobID> townKnown = tfbe.getTownVillagerHandle().getTownKnownJobs();
                    ImmutableMap.Builder<JobID, ResourceLocation> producers = ImmutableMap.builder();
                    ServerJobsRegistry.getAllJobsThatProduce(td, result)
                                      .forEach((jobId, icon) -> {
                                          if (townKnown.contains(jobId)) {
                                              producers.put(jobId, icon);
                                          }
                                      });
                    QuestownNetwork.sendToPlayer(
                            sender,
                            new WorkRequestConfirmMessage(
                                    (Ingredient) requested(),
                                    producers.build(),
                                    tfbe.getTownFlagBasePos()
                            )
                    );
                }
                case CONFIRMED -> {
                    VisitorTrigger.Triggers adv = VisitorTrigger.Triggers.FirstJobRequest;
                    AdvancementsInit.VISITOR_TRIGGER.trigger((net.minecraft.server.level.ServerPlayer) ctx.sender(), adv);
                    tfbe.getWorkHandle().requestWork(Ingredients.asWorkRequest(requested));
                    tfbe.openJobsMenu(sender, false);
                }
                case REJECTED -> tfbe.openJobsMenu(sender, true);
            }
        });

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

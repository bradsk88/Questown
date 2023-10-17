package ca.bradj.questown.jobs;

import ca.bradj.questown.integration.minecraft.MCTownItem;
import ca.bradj.questown.mobs.visitor.VisitorMobEntity;
import ca.bradj.questown.town.interfaces.TownInterface;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface Job<H extends HeldItem<H, ?>, SNAPSHOT> {
    void addStatusListener(StatusListener o);

    GathererJournal.Status getStatus();

    void initializeStatus(GathererJournal.Status s);

    void tick(
            TownInterface town,
            BlockPos entityBlockPos,
            Vec3 entityPos,
            Direction facingPos
    );

    boolean shouldDisappear(
            TownInterface town,
            Vec3 entityPosition
    );

    boolean openScreen(
            ServerPlayer sp,
            VisitorMobEntity visitorMobEntity
    );

    Container getInventory();

    SNAPSHOT getJournalSnapshot();

    void initialize(SNAPSHOT journal);

    List<Boolean> getSlotLockStatuses();

    DataSlot getLockSlot(int i);

    @Nullable BlockPos getTarget(
            BlockPos entityBlockPos,
            Vec3 entityPos,
            TownInterface town
    );

    void initializeItems(Iterable<H> itemz);

    boolean shouldBeNoClip(TownInterface town, BlockPos blockPos);

    TranslatableComponent getJobName();

    boolean addToEmptySlot(MCTownItem mcTownItem);
}

package ca.bradj.questown._vanilla;

import ca.bradj.questown._vanilla.entities.EntitiesInit;
import ca.bradj.questown._vanilla.entities.FishingHook;
import ca.bradj.questown.blocks.FishingStationBlock;
import ca.bradj.questown.integration.jobs.AfterInsertItemEvent;
import ca.bradj.questown.integration.jobs.BeforeExtractEvent;
import ca.bradj.questown.integration.jobs.JobPhaseModifier;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class DeployFishingHookRule extends JobPhaseModifier {

    private List<Entity> hooks = new ArrayList<>();

    public static @Nullable FishingHook deployHere(
            ServerLevel sl,
            BlockPos blockPos,
            LivingEntity owner
    ) {
        FishingHook e = EntitiesInit.FISHIN_HOOK.get().create(sl);
        if (e == null) {
            return null;
        }
        Vec3 attachPoint = Vec3.atLowerCornerOf(blockPos.above());

        Vec3 rodTip = FishingStationBlock.getAttachPoint(blockPos, sl);
        Vec3 hookPos = Vec3.atBottomCenterOf(blockPos);
        if (rodTip != null) {
            attachPoint = rodTip;
            hookPos = FishingStationBlock.getRandomHookPos(blockPos, sl);
        }
        e.setOwner(owner, attachPoint);
        if (hookPos == null) {
            Vec3 push = Vec3.atCenterOf(blockPos);
            push = push.subtract(Vec3.atCenterOf(owner.blockPosition()));
            push = push.normalize();
            push = push.multiply(3, 1, 3);
            hookPos = Vec3.atBottomCenterOf(blockPos.offset(push.x, 0, push.z));
        }
        e.setPos(hookPos);
        sl.addFreshEntity(e);
        return e;
    }

    @Override
    public <CONTEXT> @Nullable CONTEXT afterInsertItem(
            CONTEXT ctxInput,
            AfterInsertItemEvent<CONTEXT> event
    ) {
        CONTEXT ctx = super.afterInsertItem(ctxInput, event);
        ServerLevel sl = event.world().asServerLevel();
        if (sl == null) {
            return ctx;
        }
        LivingEntity villager = (LivingEntity) sl.getEntity(event.inserter());
        @Nullable FishingHook deployed = deployHere(sl, event.workSpot().workPosition(), villager);
        if (deployed != null) {
            this.hooks.add(deployed);
        }
        return ctx;
    }

    @Override
    public <CONTEXT> @Nullable CONTEXT beforeExtract(
            CONTEXT ctxInput,
            BeforeExtractEvent<CONTEXT> event
    ) {
        CONTEXT context = super.beforeExtract(ctxInput, event);
        if (!hooks.isEmpty()) {
            hooks.forEach(h -> h.remove(Entity.RemovalReason.DISCARDED));
            hooks.clear();
        }
        return context;
    }
}

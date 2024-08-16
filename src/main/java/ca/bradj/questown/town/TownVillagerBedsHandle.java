package ca.bradj.questown.town;

import ca.bradj.questown.blocks.HospitalBedBlock;
import ca.bradj.questown.town.interfaces.RoomsHolder;
import ca.bradj.questown.town.interfaces.TownInterface;
import com.google.common.collect.ImmutableList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public class TownVillagerBedsHandle {

    private final VillagerBedsHandle<BlockPos, LivingEntity, TownInterface> delegate = new VillagerBedsHandle<>(
            TownVillagerBedsHandle::getAllBedHeads,
            TownVillagerBedsHandle::getHealingFactor,
            TownVillagerBedsHandle::getDamageTicksLeft,
            TownVillagerBedsHandle::getDistance
    );

    private static double getDistance(
            LivingEntity e,
            BlockPos pos
    ) {
        return e.distanceToSqr(Vec3.atCenterOf(pos));
    }

    private static Integer getDamageTicksLeft(
            TownInterface townInterface,
            LivingEntity uuid
    ) {
        return townInterface.getVillagerHandle().getDamageTicksLeft(uuid.getUUID());
    }

    private static Double getHealingFactor(
            TownInterface townInterface,
            BlockPos pos
    ) {
        return townInterface.getHealHandle().getHealFactor(pos);
    }

    public TownVillagerBedsHandle() {
    }

    private static Collection<BlockPos> getAllBedHeads(@NotNull TownInterface town) {
        RoomsHolder rooms = town.getRoomHandle();
        Collection<BlockPos> beds = rooms.findMatchedRecipeBlocks(
                i -> i instanceof BedBlock || i instanceof HospitalBedBlock
        );
        return beds.stream().filter(bp -> isHead(town, bp)).toList();
    }

    private static boolean isHead(
            TownInterface town,
            BlockPos i
    ) {
        BedPart value = town.getServerLevel().getBlockState(i).getValue(BlockStateProperties.BED_PART);
        return BedPart.HEAD.equals(value);
    }

    public void tick(
            TownFlagBlockEntity town,
            ImmutableList<LivingEntity> entities
    ) {
        delegate.tick(town, entities);
    }

    public void claim(
            LivingEntity uuid,
            TownFlagBlockEntity town
    ) {
        delegate.claim(uuid, town);
    }

    public Optional<GlobalPos> getBestBed(TownInterface town, LivingEntity ent) {
        // TODO[ASAP]: Handle no beds left
        return Optional.of(GlobalPos.of(town.getServerLevel().dimension(), delegate.getBestBed(ent)));
    }
}

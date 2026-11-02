package ca.bradj.questown.jobs;

import ca.bradj.questown.jobs.production.ProductionStatus;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.FurnaceBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static net.minecraft.world.level.block.Blocks.CHEST;

/** A warp assigns a job its block by block type alone, even when the slot it needs isn't filled yet. */
class WorkIsJobBlockAtTest {

    private static final BlockPos POS = new BlockPos(0, 64, 0);

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static Work ovenLikeJob() {
        JobID id = new JobID("baker", "test_oven_like");
        return new Work(
                id,
                null,
                ItemStack.EMPTY,
                null,
                null,
                ctx -> ctx.blockInfo().state(ctx.blockPos()).getBlock() == Blocks.CHEST
                        && ctx.blockInfo().entity(ctx.blockPos()) instanceof ChestBlockEntity,
                (info, pos) -> info.state(pos).getBlock() == Blocks.CHEST,
                new net.minecraft.resources.ResourceLocation("questown", "test_room"),
                ProductionStatus.FACTORY.idle(),
                td -> ImmutableSet.of(),
                sl -> null,
                items -> List.of(),
                input -> ca.bradj.questown.town.NoOpWarper.INSTANCE,
                0,
                false,
                ImmutableList.of()
        );
    }

    @Test
    void isJobBlockAt_matchesTheJobBlockNotTheFurnace() {
        Work job = ovenLikeJob();
        BlockState chest = CHEST.defaultBlockState();
        BlockEntity chestBe = new ChestBlockEntity(POS, chest);
        BlockState furnace = Blocks.FURNACE.defaultBlockState();
        BlockEntity furnaceBe = new FurnaceBlockEntity(POS, furnace);

        Assertions.assertTrue(
                job.isJobBlockAt(ServerJobsRegistry.blockInfo(chest, chestBe), POS),
                "the chest stands in for the job's own block — the identity check must match it"
        );
        Assertions.assertFalse(
                job.isJobBlockAt(ServerJobsRegistry.blockInfo(furnace, furnaceBe), POS),
                "a furnace is not this job's block — the old class-sniff would have matched it"
        );
    }

    @Test
    void isJobBlockAt_isContentAgnostic() {
        Work job = ovenLikeJob();
        BlockState chest = CHEST.defaultBlockState();
        Assertions.assertTrue(job.isJobBlockAt(ServerJobsRegistry.blockInfo(chest, null), POS));
    }
}

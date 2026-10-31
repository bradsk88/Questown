package ca.bradj.questown.world;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;

/**
 * Full-suite autotest hang (2026-10-13, test 16/58 arborist/cut_trees): the
 * warp's tree-chop flood fill read the live level for a chunk that was
 * generated but evicted from the chunk cache; the main-thread managed block
 * waited on the chunk system the main thread itself blocks, and the
 * watchdog killed the server after 600 s. The lookup must instead check
 * chunk PRESENCE (never blocks) and treat an unloaded chunk as the region
 * boundary.
 */
class WarpWorldAccessChunkBoundaryTest {

    @BeforeAll
    static void init() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    private static ServerLevel levelWith(boolean chunkLoaded) {
        ServerLevel level = Mockito.mock(ServerLevel.class);
        ServerChunkCache source = Mockito.mock(ServerChunkCache.class);
        Mockito.when(level.getChunkSource()).thenReturn(source);
        Mockito.when(source.hasChunk(anyInt(), anyInt())).thenReturn(chunkLoaded);
        Mockito.when(level.getBlockState(any(BlockPos.class)))
                .thenReturn(Blocks.OAK_LOG.defaultBlockState());
        return level;
    }

    @Test
    void unloaded_chunk_resolves_to_null_instead_of_reading_the_level() {
        ServerLevel level = levelWith(false);
        BlockPos pos = new BlockPos(4096, 64, -4096);

        assertNull(WarpWorldAccess.resolveLevelBlockState(level, pos),
                "an unloaded chunk must be the region boundary, not a live read");
        // The live level must never be consulted for a block read.
        Mockito.verify(level, Mockito.never()).getBlockState(any(BlockPos.class));
    }

    @Test
    void loaded_chunk_reads_the_level() {
        ServerLevel level = levelWith(true);
        BlockPos pos = new BlockPos(4096, 64, -4096);

        BlockState bs = WarpWorldAccess.resolveLevelBlockState(level, pos);
        assertEquals(Blocks.OAK_LOG.defaultBlockState(), bs);
        Mockito.verify(level).getBlockState(pos);
    }
}

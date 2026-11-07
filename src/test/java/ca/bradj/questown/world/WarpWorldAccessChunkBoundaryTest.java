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

/** The warp's chop never reads an unloaded chunk; it treats one as the region's edge. Reading it would hang the server. */
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

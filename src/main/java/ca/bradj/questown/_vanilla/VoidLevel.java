package ca.bradj.questown._vanilla;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkSource;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.ticks.LevelTickAccess;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Predicate;

@SuppressWarnings("NullableProblems")
public class VoidLevel implements WorldGenLevel {
    private final ServerLevel delegate;

    public VoidLevel(ServerLevel delegate) {
        this.delegate = delegate;
    }

    @Override
    public long getSeed() {
        return this.delegate.getSeed();
    }

    @Override
    public ServerLevel getLevel() {
        throw new UnsupportedOperationException("VoidLevel does not support getLevel()");
    }

    @Override
    public long nextSubTickCount() {
        return this.delegate.nextSubTickCount();
    }

    @Override
    public LevelTickAccess<Block> getBlockTicks() {
        return this.delegate.getBlockTicks();
    }

    @Override
    public LevelTickAccess<Fluid> getFluidTicks() {
        return this.delegate.getFluidTicks();
    }

    @Override
    public LevelData getLevelData() {
        return this.delegate.getLevelData();
    }

    @Override
    public DifficultyInstance getCurrentDifficultyAt(BlockPos p_46800_) {
        return this.delegate.getCurrentDifficultyAt(p_46800_);
    }

    @Override
    public @Nullable MinecraftServer getServer() {
        throw new UnsupportedOperationException("VoidLevel does not support getServer()");
    }

    @Override
    public ChunkSource getChunkSource() {
        throw new UnsupportedOperationException("VoidLevel does not support getChunkSource()");
    }

    @Override
    public RandomSource getRandom() {
        return delegate.random;
    }

    @Override
    public void playSound(
            @Nullable Player p_46775_,
            BlockPos p_46776_,
            SoundEvent p_46777_,
            SoundSource p_46778_,
            float p_46779_,
            float p_46780_
    ) {
        // No sound in void level
    }

    @Override
    public void addParticle(
            ParticleOptions p_46783_,
            double p_46784_,
            double p_46785_,
            double p_46786_,
            double p_46787_,
            double p_46788_,
            double p_46789_
    ) {
        // No particles in void level
    }

    @Override
    public void levelEvent(
            @Nullable Player p_46771_,
            int p_46772_,
            BlockPos p_46773_,
            int p_46774_
    ) {
        // No level events in void level
    }

    @Override
    public void gameEvent(
            GameEvent p_220404_,
            Vec3 p_220405_,
            GameEvent.Context p_220406_
    ) {
        // No game events in void level
    }

    @Override
    public RegistryAccess registryAccess() {
        throw new UnsupportedOperationException("VoidLevel does not support registryAccess()");
    }

    @Override
    public float getShade(
            Direction p_45522_,
            boolean p_45523_
    ) {
        return delegate.getShade(p_45522_, p_45523_);
    }

    @Override
    public LevelLightEngine getLightEngine() {
        return delegate.getLightEngine();
    }

    @Override
    public WorldBorder getWorldBorder() {
        return delegate.getWorldBorder();
    }

    @Override
    public @Nullable BlockEntity getBlockEntity(BlockPos p_45570_) {
        return delegate.getBlockEntity(p_45570_);
    }

    @Override
    public BlockState getBlockState(BlockPos p_45571_) {
        return delegate.getBlockState(p_45571_);
    }

    @Override
    public FluidState getFluidState(BlockPos p_45569_) {
        return delegate.getFluidState(p_45569_);
    }

    @Override
    public List<Entity> getEntities(
            @Nullable Entity p_45936_,
            AABB p_45937_,
            Predicate<? super Entity> p_45938_
    ) {
        return delegate.getEntities(p_45936_, p_45937_, p_45938_);
    }

    @Override
    public <T extends Entity> List<T> getEntities(
            EntityTypeTest<Entity, T> p_151464_,
            AABB p_151465_,
            Predicate<? super T> p_151466_
    ) {
        return delegate.getEntities(p_151464_, p_151465_, p_151466_);
    }

    @Override
    public List<? extends Player> players() {
        return delegate.players();
    }

    @Override
    public @Nullable ChunkAccess getChunk(
            int p_46823_,
            int p_46824_,
            ChunkStatus p_46825_,
            boolean p_46826_
    ) {
        return delegate.getChunk(p_46823_, p_46824_, p_46825_, p_46826_);
    }

    @Override
    public int getHeight(
            Heightmap.Types p_46827_,
            int p_46828_,
            int p_46829_
    ) {
        return delegate.getHeight(p_46827_, p_46828_, p_46829_);
    }

    @Override
    public int getSkyDarken() {
        return delegate.getSkyDarken();
    }

    @Override
    public BiomeManager getBiomeManager() {
        return delegate.getBiomeManager();
    }

    @Override
    public Holder<Biome> getUncachedNoiseBiome(
            int p_204159_,
            int p_204160_,
            int p_204161_
    ) {
        return delegate.getUncachedNoiseBiome(p_204159_, p_204160_, p_204161_);
    }

    @Override
    public boolean isClientSide() {
        return delegate.isClientSide;
    }

    @Override
    public int getSeaLevel() {
        return delegate.getSeaLevel();
    }

    @Override
    public DimensionType dimensionType() {
        return delegate.dimensionType();
    }

    @Override
    public boolean isStateAtPosition(
            BlockPos p_46938_,
            Predicate<BlockState> p_46939_
    ) {
        return delegate.isStateAtPosition(p_46938_, p_46939_);
    }

    @Override
    public boolean isFluidAtPosition(
            BlockPos p_151584_,
            Predicate<FluidState> p_151585_
    ) {
        return delegate.isFluidAtPosition(p_151584_, p_151585_);
    }

    @Override
    public boolean setBlock(
            BlockPos p_46947_,
            BlockState p_46948_,
            int p_46949_,
            int p_46950_
    ) {
        return true;
    }

    @Override
    public boolean removeBlock(
            BlockPos p_46951_,
            boolean p_46952_
    ) {
        return true;
    }

    @Override
    public boolean destroyBlock(
            BlockPos p_46957_,
            boolean p_46958_,
            @Nullable Entity p_46959_,
            int p_46960_
    ) {
        return true;
    }
}

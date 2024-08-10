package ca.bradj.questown.town;

import ca.bradj.questown.core.Config;
import ca.bradj.questown.core.init.items.ItemsInit;
import ca.bradj.questown.integration.minecraft.MCContainer;
import ca.bradj.questown.integration.minecraft.MCTownItem;
import ca.bradj.questown.items.GathererMap;
import ca.bradj.questown.jobs.gatherer.Loots;
import ca.bradj.questown.jobs.leaver.ContainerTarget;
import com.google.common.collect.ImmutableSet;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

public class TownKnownBiomes {
    private final UnsafeTown town = new UnsafeTown();

    private final ArrayList<Biome> nearbyBiomes = new ArrayList<>();
    private boolean initialized = false;

    public void initialize(TownFlagBlockEntity t) {
        this.town.initialize(t);
        this.initialized = true;
    }

    public Collection<ResourceLocation> getAllInTown() {
        @NotNull TownFlagBlockEntity e = town.getUnsafe();
        ImmutableSet.Builder<ResourceLocation> b = ImmutableSet.builder();
        List<ContainerTarget<MCContainer, MCTownItem>> cs = TownContainers.getAllContainers(e, e.getServerLevel());
        cs.forEach(v -> v.getItems()
                .stream()
                .filter(i -> ItemsInit.GATHERER_MAP.get()
                        .equals(i.get()))
                .map(i -> GathererMap.getBiome(i.toItemStack()))
                .filter(Objects::nonNull)
                .forEach(b::add));
        nearbyBiomes.forEach(v -> {
            ResourceLocation key = ForgeRegistries.BIOMES.getKey(v);
            if (key == null) {
                return;
            }
            b.add(key);
        });
        b.add(Loots.fallbackBiome);
        return b.build();
    }

    public ResourceLocation getRandomNearbyBiome() {
        if (nearbyBiomes.isEmpty()) {
            computeNearbyBiomes(this);
        }
        Biome biome = Util.getRandom(nearbyBiomes, town.getServerLevelUnsafe().getRandom());
        return ForgeRegistries.BIOMES.getKey(biome);
    }

    public void tick() {
        if (nearbyBiomes.isEmpty()) {
            computeNearbyBiomes(this);
        }
    }

    private static void computeNearbyBiomes(
            TownKnownBiomes e
    ) {
        TownFlagBlockEntity town = e.town.getUnsafe();
        BlockPos blockPos = town.getBlockPos();
        ServerLevel level = e.town. getServerLevelUnsafe();
        ChunkPos here = new ChunkPos(blockPos);
        Biome value = level.getBiome(blockPos).value();
        e.nearbyBiomes.add(value);
        for (Direction d : Direction.Plane.HORIZONTAL) {
            for (int i = 0; i < Config.BIOME_SCAN_RADIUS.get(); i++) {
                ChunkPos there = new ChunkPos(here.x + d.getStepX() * i, here.z + d.getStepZ() * i);
                Biome biome = level.getBiome(there.getMiddleBlockPosition(blockPos.getY()))
                        .value();
                e.nearbyBiomes.add(biome);
            }
        }
    }

    public boolean isInitialized() {
        return initialized;
    }
}

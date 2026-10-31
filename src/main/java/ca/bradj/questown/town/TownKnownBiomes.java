package ca.bradj.questown.town;

import ca.bradj.questown.QT;
import ca.bradj.questown.loader.Loader;
import ca.bradj.questown.core.Config;
import ca.bradj.questown.core.init.items.ItemsInit;
import ca.bradj.questown.integration.minecraft.MCContainer;
import ca.bradj.questown.integration.minecraft.MCTownItem;
import ca.bradj.questown.items.GathererMap;
import ca.bradj.questown.jobs.gatherer.Loots;
import ca.bradj.questown.jobs.leaver.ContainerTarget;
import ca.bradj.questown.town.entity.TownFlagBlockEntity;
import com.google.common.collect.ImmutableSet;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

public class TownKnownBiomes {
    private final UnsafeTown town = new UnsafeTown(getClass());

    private final ArrayList<Holder<Biome>> nearbyBiomes = new ArrayList<>();
    private boolean initialized = false;

    public void initialize(TownFlagBlockEntity t) {
        this.town.initialize(t);
        this.initialized = true;
    }

    public Collection<ResourceLocation> getAllInTown() {
        ImmutableSet.Builder<ResourceLocation> b = ImmutableSet.builder();
        try {
            getBiomesFromMapsInChests(b);
        } catch (IllegalStateException ex) {
            QT.FLAG_LOGGER.error("TownKnownBiomes is not connected to town. Fallback biome will be used");
        }
        nearbyBiomes.forEach(v -> {
            ResourceLocation key = Loader.biomeKeyOf(v);
            if (key == null) {
                return;
            }
            b.add(key);
        });
        b.add(Loots.fallbackBiome);
        return b.build();
    }

    private void getBiomesFromMapsInChests(ImmutableSet.Builder<ResourceLocation> b) {
        @NotNull TownFlagBlockEntity e = town.getUnsafe();
        List<ContainerTarget<MCContainer, MCTownItem>> cs = TownContainers.getAllContainers(e, e.getServerLevel());
        for (ContainerTarget<MCContainer, MCTownItem> c : cs) {
            for (ResourceLocation mapItem : getMapItems(c)) {
                b.add(mapItem);
            }
        }
    }

    private static @NotNull List<ResourceLocation> getMapItems(ContainerTarget<MCContainer, MCTownItem> v) {
        return v.getItems()
                .stream()
                .filter(i -> ItemsInit.GATHERER_MAP.get()
                                                   .equals(i.get()))
                .map(i -> GathererMap.getBiome(i.toQTItemStack()))
                .filter(Objects::nonNull).toList();
    }

    public ResourceLocation getRandomNearbyBiome() {
        if (nearbyBiomes.isEmpty()) {
            computeNearbyBiomes(this);
        }
        Holder<Biome> biome = Util.getRandom(nearbyBiomes, town.getServerLevelUnsafe().getRandom());
        return Loader.biomeKeyOf(biome);
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
        ServerLevel level = e.town.getServerLevelUnsafe();
        ChunkPos here = new ChunkPos(blockPos);
        e.nearbyBiomes.add(level.getBiome(blockPos));
        for (Direction d : Direction.Plane.HORIZONTAL) {
            for (int i = 0; i < Config.BIOME_SCAN_RADIUS.get(); i++) {
                ChunkPos there = new ChunkPos(here.x + d.getStepX() * i, here.z + d.getStepZ() * i);
                e.nearbyBiomes.add(level.getBiome(there.getMiddleBlockPosition(blockPos.getY())));
            }
        }
    }

    public boolean isInitialized() {
        return initialized;
    }
}

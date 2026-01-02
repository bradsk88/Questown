package ca.bradj.questown.town.entity;

import ca.bradj.questown.QT;
import ca.bradj.questown.commands.DebugLogArgument;
import ca.bradj.questown.core.VillagerUUID;
import ca.bradj.questown.mc.Util;
import ca.bradj.questown.town.UnsafeTown;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class TownVillagerRenderingHandle {

    UnsafeTown town = new UnsafeTown(getClass());

    public void associate(TownFlagBlockEntity t) {
        town.initialize(t);
    }

    private record LookTarget(Entity who, Long untilTick) {
    }

    private final Map<VillagerUUID, LookTarget> lookTargets = new HashMap<>();
    private final Map<VillagerUUID, LookTarget> mostRecentLookTarget = new HashMap<>();

    public Optional<Entity> getLookTarget(@Nullable UUID vuid) {
        LookTarget tar = lookTargets.get(vuid);
        if (tar == null) {
            return Optional.empty();
        }
        long currentTick = Util.getTick(town.getServerLevelUnsafe());
        if (currentTick < tar.untilTick()) {
            return Optional.of(tar.who);
        }
        town.getUnsafe().getDebugLogger(QT.VILLAGER_LOGGER, DebugLogArgument.VILLAGER_NAVIGATION)
            .log("Look target for {} has expired at tick {} (current {})", vuid, tar.untilTick(), currentTick);
        lookTargets.remove(vuid);
        return Optional.empty();
    }

    public void setLookTarget(
            @Nullable VillagerUUID vuid,
            Entity entity,
            long untilTick,
            long thenNotUntilTick
    ) {
        LookTarget tar = mostRecentLookTarget.get(vuid);
        if (tar != null && tar.who.equals(entity)) {
            long currentTick = Util.getTick(town.getServerLevelUnsafe());
            if (currentTick < tar.untilTick()) {
                return;
            }
            mostRecentLookTarget.remove(vuid);
        }
        lookTargets.computeIfAbsent(
                vuid, (k) -> {
                    mostRecentLookTarget.put(k, new LookTarget(entity, thenNotUntilTick));
                    LookTarget lookTarget = new LookTarget(entity, untilTick);
                    town.getUnsafe().getDebugLogger(QT.VILLAGER_LOGGER, DebugLogArgument.VILLAGER_NAVIGATION).log(
                            "Setting look target for {} to {} until tick {} (then not until {})",
                            vuid,
                            entity,
                            untilTick,
                            thenNotUntilTick
                    );
                    return lookTarget;
                }
        );
    }
}

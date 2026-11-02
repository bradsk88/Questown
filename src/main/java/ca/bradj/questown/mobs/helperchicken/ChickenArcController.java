package ca.bradj.questown.mobs.helperchicken;

import ca.bradj.questown.QT;
import ca.bradj.questown.mc.Compat;
import ca.bradj.questown.town.entity.TownFlagBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

import java.util.List;

/** Runs the helper chicken's lessons for a flag each tick: reads the world, saves the new lesson, and updates the chicken's bubble. */
public final class ChickenArcController {

    private static final int CHICKEN_SEARCH_RANGE = 64;

    private ChickenArcController() {
    }

    private static long lastTickDiagTick = -1L;

    public static void tick(TownFlagBlockEntity flag) {
        // Only in held autotest runs, or it floods the log.
        if ("true".equals(System.getProperty("questown.autotest.hold"))) {
            ServerLevel diagLevel = flag.getServerLevel();
            long gt = diagLevel == null ? 0L : diagLevel.getGameTime();
            if (gt - lastTickDiagTick >= 200L) {
                lastTickDiagTick = gt;
                Player nearest = ChickenArcConditions.findNearestPlayerForFlag(flag);
                QT.JOB_LOGGER.info(
                        "[chicken-arc] tick diag: spawned={} forfeit={} state={} initialized={} level={} nearest={} nearestHasWand={}",
                        flag.getChickenEverSpawned(), flag.getChickenArcForfeit(),
                        flag.getChickenBeatState(), flag.isInitialized(), diagLevel != null,
                        nearest == null ? "null" : nearest.getName(),
                        ChickenArcConditions.hasWandInInventory(nearest));
            }
        }
        if (!flag.getChickenEverSpawned()) {
            return;
        }
        if (flag.getChickenArcForfeit()) {
            return;
        }
        ChickenBeatState current = flag.getChickenBeatState();
        if (current == ChickenBeatState.COMPLETE || current == ChickenBeatState.FORFEIT) {
            return;
        }
        ServerLevel level = flag.getServerLevel();
        if (level == null) {
            return;
        }

        ChickenArcTransitions.Observed observed = ChickenArcConditions.observe(flag);
        ChickenBeatState advanced = ChickenArcTransitions.advance(current, observed);
        if (advanced != current) {
            ca.bradj.questown.QT.JOB_LOGGER.info(
                    "[chicken-arc] beat advanced {} -> {} (playerHasWand={}, campfireLit={}, sleepHappenedToday={}, signPlacedAsJobBoard={}, chestPlaced={})",
                    current, advanced,
                    observed.playerHasWand(), observed.campfireLit(),
                    observed.sleepHappenedToday(), observed.signPlacedAsJobBoard(),
                    observed.chestPlaced()
            );
            persistBeatState(flag, advanced);
            clearTransitionedObservations(flag, current, advanced);
            clicksByPlayer.clear();
        }
        updateChickenBubble(level, flag, advanced);
    }

    private static void persistBeatState(
            TownFlagBlockEntity flag,
            ChickenBeatState newState
    ) {
        flag.setChickenBeatState(newState);
        CompoundTag tag = Compat.getBlockStoredTagData(flag);
        flag.writeTownData(tag);
        flag.setChanged();
    }

    /** Clears the one-time observations a lesson change used, so they can't move a later lesson. */
    private static void clearTransitionedObservations(
            TownFlagBlockEntity flag,
            ChickenBeatState from,
            ChickenBeatState to
    ) {
        if (passedThrough(from, to, ChickenBeatState.SUNSET_AND_MAP)) {
            flag.setChickenObservedSleepSinceSunset(false);
        }
        if (passedThrough(from, to, ChickenBeatState.WAITING_FOR_VILLAGER_UI)) {
            flag.setChickenObservedVillagerUiOpen(false);
        }
        if (passedThrough(from, to, ChickenBeatState.WAITING_FOR_FLAG_UI)) {
            flag.setChickenObservedFlagUiOpen(false);
        }
        if (passedThrough(from, to, ChickenBeatState.AWAITING_WORLDLY_SEEDS_DELIVERY)) {
            flag.setChickenObservedSeedsGiven(false);
        }
    }

    private static boolean passedThrough(
            ChickenBeatState from,
            ChickenBeatState to,
            ChickenBeatState candidate
    ) {
        return from.ordinal() <= candidate.ordinal() && candidate.ordinal() < to.ordinal();
    }

    private static void updateChickenBubble(
            ServerLevel level,
            TownFlagBlockEntity flag,
            ChickenBeatState state
    ) {
        HelperChickenEntity chicken = findChickenForFlag(level, flag);
        if (chicken == null) {
            warnChickenNotFound(flag, state);
            return;
        }
        Player nearestPlayer = ChickenArcConditions.findNearestPlayerForFlag(flag);
        boolean hasItem = ChickenArcConditions.playerHoldsRequiredItem(nearestPlayer, state);
        boolean hasPressurePlate = ChickenArcConditions.playerHoldsPressurePlate(nearestPlayer);
        boolean chestSpawned = flag.getChickenSunsetChestSpawned();
        boolean isNight = level.isNight();
        boolean seedsInContainer = ChickenArcConditions.areWorldlySeedsInAnyContainer(flag);
        boolean gathererLeft = ChickenArcConditions.isGathererLeft(flag);
        PhaseInputs phaseInputs = new PhaseInputs(hasItem, chestSpawned, isNight, hasPressurePlate, seedsInContainer, gathererLeft);
        Presentation presentation = ChickenArcPresentation.present(state, phaseInputs);
        ChickenArcBubbles.Bubble bubble = presentation.bubble();
        String newTexture = bubble.textureIcon() == null ? "" : bubble.textureIcon().toString();
        String newHintKey = presentation.hintKey() == null ? "" : presentation.hintKey();
        boolean changed = !net.minecraft.world.item.ItemStack.matches(chicken.getBubbleIconA(), bubble.iconA())
                || !net.minecraft.world.item.ItemStack.matches(chicken.getBubbleIconB(), bubble.iconB())
                || chicken.isThroughWalls() != bubble.throughWalls()
                || !chicken.getBubbleTexturePath().equals(newTexture)
                || !chicken.getHintKey().equals(newHintKey);
        chicken.setBubbleIconA(bubble.iconA());
        chicken.setBubbleIconB(bubble.iconB());
        chicken.setThroughWalls(bubble.throughWalls());
        chicken.setBubbleTexturePath(newTexture);
        chicken.setHintKey(newHintKey);
        if (changed) {
            QT.JOB_LOGGER.info(
                    "[chicken-arc] bubble updated: state={} hasItem={} iconA={} iconB={} throughWalls={}",
                    state, hasItem, bubble.iconA(), bubble.iconB(), bubble.throughWalls()
            );
        }
    }

    private static long lastChickenNotFoundLogTick = -1L;

    private static void warnChickenNotFound(TownFlagBlockEntity flag, ChickenBeatState state) {
        ServerLevel sl = flag.getServerLevel();
        long tick = sl == null ? 0L : sl.getGameTime();
        if (tick - lastChickenNotFoundLogTick < 100L) {
            return;
        }
        lastChickenNotFoundLogTick = tick;
        QT.JOB_LOGGER.info(
                "[chicken-arc] no chicken matches flag at {} (state={}); ownerFlagPos likely null after chunk reload",
                flag.getTownFlagBasePos(), state
        );
    }

    private static HelperChickenEntity findChickenForFlag(
            ServerLevel level,
            TownFlagBlockEntity flag
    ) {
        BlockPos flagPos = flag.getTownFlagBasePos();
        AABB search = new AABB(flagPos).inflate(CHICKEN_SEARCH_RANGE);
        List<HelperChickenEntity> matches = level.getEntitiesOfClass(
                HelperChickenEntity.class,
                search,
                e -> flagPos.equals(e.getOwnerFlagPos())
        );
        if (!matches.isEmpty()) {
            return matches.get(0);
        }
        // A chicken with no owner flag adopts this one rather than a second chicken spawning.
        List<HelperChickenEntity> orphans = level.getEntitiesOfClass(
                HelperChickenEntity.class,
                search,
                e -> e.getOwnerFlagPos() == null
        );
        if (orphans.isEmpty()) {
            return null;
        }
        HelperChickenEntity adopted = orphans.get(0);
        adopted.setOwnerFlagPos(flagPos);
        QT.JOB_LOGGER.info(
                "[chicken-arc] adopted orphan chicken {} for flag at {}",
                adopted.getUUID(), flagPos
        );
        return adopted;
    }

    /** The player's clicks on the current lesson; a new lesson starts the count over. */
    private record BeatClicks(ChickenBeatState state, int count) {}
    private static final java.util.Map<java.util.UUID, BeatClicks> clicksByPlayer =
            new java.util.concurrent.ConcurrentHashMap<>();
    /** Every third click on the same lesson shows the plain-language hint instead of the chicken's line. */
    private static final int PLAIN_TEXT_CYCLE = 3;

    public static void onPlayerClickedChicken(
            net.minecraft.server.level.ServerPlayer player,
            TownFlagBlockEntity flag
    ) {
        ChickenBeatState state = flag.getChickenBeatState();
        boolean hasItem = ChickenArcConditions.playerHoldsRequiredItem(player, state);
        boolean hasWand = ChickenArcConditions.hasWandInInventory(player);
        boolean isNight = player.level().isNight();
        boolean chestSpawned = flag.getChickenSunsetChestSpawned();
        boolean hasPressurePlate = ChickenArcConditions.playerHoldsPressurePlate(player);
        int clickCount = recordClick(player.getUUID(), state);
        ca.bradj.questown.QT.JOB_LOGGER.info(
                "[chicken-arc] click handler: state={} hasMatchingItem={} hasWandInInv={} clickCount={}",
                state, hasItem, hasWand, clickCount
        );
        boolean seedsInContainer = ChickenArcConditions.areWorldlySeedsInAnyContainer(flag);
        boolean gathererLeft = ChickenArcConditions.isGathererLeft(flag);
        Presentation presentation = ChickenArcPresentation.present(
                state, new PhaseInputs(hasItem, chestSpawned, isNight, hasPressurePlate, seedsInContainer, gathererLeft)
        );
        String key = clickCount % PLAIN_TEXT_CYCLE == 0
                ? presentation.plainKey()
                : presentation.hintKey();
        if (key == null) {
            return;
        }
        ca.bradj.questown.mc.Util.onScreenText(() -> player, key);
    }

    private static int recordClick(java.util.UUID uuid, ChickenBeatState state) {
        BeatClicks updated = clicksByPlayer.compute(uuid, (k, prior) -> {
            if (prior == null || prior.state() != state) {
                return new BeatClicks(state, 1);
            }
            return new BeatClicks(state, prior.count() + 1);
        });
        return updated.count();
    }

}

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

/**
 * Per-flag, per-tick driver for the helper-chicken onboarding arc (U4).
 *
 * <p>Owns the side effects the pure machine ({@link ChickenArcTransitions}) must
 * not: reading observations via {@link ChickenArcConditions}, persisting the
 * advanced state back to the flag BE, and pushing the matching bubble into the
 * chicken's {@link net.minecraft.network.syncher.SynchedEntityData}.
 *
 * <p>The controller also clears ephemeral observation flags after they've
 * driven a transition — the sleep observation fires once and must not
 * re-trigger on subsequent ticks.
 */
public final class ChickenArcController {

    private static final int CHICKEN_SEARCH_RANGE = 64;

    private ChickenArcController() {
    }

    public static void tick(TownFlagBlockEntity flag) {
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

    /**
     * Resets ephemeral observation flags that a transition consumed, so they
     * cannot re-fire on a later beat. For example: {@code sleepSinceSunset}
     * advances only SUNSET_AND_MAP; after that it should read false again so
     * the next day's sleep doesn't double-advance a later beat.
     */
    private static void clearTransitionedObservations(
            TownFlagBlockEntity flag,
            ChickenBeatState from,
            ChickenBeatState to
    ) {
        // SUNSET_AND_MAP was the only beat consuming the sleep observation.
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
        boolean chestSpawned = flag.getChickenSunsetChestSpawned();
        boolean isNight = level.isNight();
        PhaseInputs phaseInputs = new PhaseInputs(hasItem, chestSpawned, isNight);
        ChickenArcBubbles.Bubble bubble = ChickenArcPresentation.present(state, phaseInputs).bubble();
        String newTexture = bubble.textureIcon() == null ? "" : bubble.textureIcon().toString();
        boolean changed = !net.minecraft.world.item.ItemStack.matches(chicken.getBubbleIconA(), bubble.iconA())
                || !net.minecraft.world.item.ItemStack.matches(chicken.getBubbleIconB(), bubble.iconB())
                || chicken.isThroughWalls() != bubble.throughWalls()
                || !chicken.getBubbleTexturePath().equals(newTexture);
        chicken.setBubbleIconA(bubble.iconA());
        chicken.setBubbleIconB(bubble.iconB());
        chicken.setThroughWalls(bubble.throughWalls());
        chicken.setBubbleTexturePath(newTexture);
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
        // Recovery path: chickens saved before ownerFlagPos was persisted have
        // a null owner. Adopt any nearby orphan whose owner is unset rather
        // than spawning a duplicate — this also covers /kill+respawn.
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

    /**
     * Sends the lang-key hint text for the current beat to a player. Called
     * from {@link HelperChickenEntity}'s click-to-interact handler — left- and
     * right-click both route here.
     */
    /**
     * Per-player click counter for the current beat. Resets implicitly when the
     * beat changes — a stored counter for state X is invalidated as soon as the
     * player's first click on state Y arrives. Used to switch monologue hints
     * over to a fourth-wall plain-text explanation after the third click.
     */
    private record BeatClicks(ChickenBeatState state, int count) {}
    private static final java.util.Map<java.util.UUID, BeatClicks> clicksByPlayer =
            new java.util.concurrent.ConcurrentHashMap<>();
    /**
     * Click cadence at which we break the fourth wall. Every Nth click on the
     * same beat shows the bracketed plain-text hint instead of the monologue;
     * the cycle then resets so subsequent clicks return to monologue.
     */
    private static final int PLAIN_TEXT_CYCLE = 3;

    public static void onPlayerClickedChicken(
            net.minecraft.server.level.ServerPlayer player,
            TownFlagBlockEntity flag
    ) {
        ChickenBeatState state = flag.getChickenBeatState();
        boolean hasItem = ChickenArcConditions.playerHoldsRequiredItem(player, state);
        boolean hasWand = ChickenArcConditions.hasWandInInventory(player);
        boolean isNight = player.getLevel().isNight();
        boolean chestSpawned = flag.getChickenSunsetChestSpawned();
        int clickCount = recordClick(player.getUUID(), state);
        ca.bradj.questown.QT.JOB_LOGGER.info(
                "[chicken-arc] click handler: state={} hasMatchingItem={} hasWandInInv={} clickCount={}",
                state, hasItem, hasWand, clickCount
        );
        Presentation presentation = ChickenArcPresentation.present(
                state, new PhaseInputs(hasItem, chestSpawned, isNight)
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

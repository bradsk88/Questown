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
            persistBeatState(flag, advanced);
            clearTransitionedObservations(flag, current, advanced);
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
        ChickenArcBubbles.Bubble bubble = ChickenArcBubbles.forState(state, hasItem);
        boolean changed = !net.minecraft.world.item.ItemStack.matches(chicken.getBubbleIconA(), bubble.iconA())
                || !net.minecraft.world.item.ItemStack.matches(chicken.getBubbleIconB(), bubble.iconB())
                || chicken.isThroughWalls() != bubble.throughWalls();
        chicken.setBubbleIconA(bubble.iconA());
        chicken.setBubbleIconB(bubble.iconB());
        chicken.setThroughWalls(bubble.throughWalls());
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
        if (matches.isEmpty()) {
            return null;
        }
        return matches.get(0);
    }

    /**
     * Sends the lang-key hint text for the current beat to a player. Called
     * from {@link HelperChickenEntity}'s click-to-interact handler — left- and
     * right-click both route here.
     */
    public static void onPlayerClickedChicken(
            net.minecraft.server.level.ServerPlayer player,
            TownFlagBlockEntity flag
    ) {
        ChickenBeatState state = flag.getChickenBeatState();
        String key = hintKey(state);
        if (key == null) {
            return;
        }
        ca.bradj.questown.mc.Util.onScreenText(() -> player, key);
    }

    private static String hintKey(ChickenBeatState state) {
        return switch (state) {
            case WAITING_FOR_STICK -> "message.questown.chicken.hint.stick";
            case WAITING_FOR_WAND_ON_CAMPFIRE -> "message.questown.chicken.hint.wand_on_campfire";
            case SUNSET_AND_MAP -> "message.questown.chicken.hint.sunset";
            case WAITING_FOR_WALL_BLOCK -> "message.questown.chicken.hint.wall_block";
            case WAITING_FOR_DOOR -> "message.questown.chicken.hint.door";
            case WAITING_FOR_WAND_ON_DOOR -> "message.questown.chicken.hint.wand_on_door";
            case WAITING_FOR_SIGN -> "message.questown.chicken.hint.sign";
            case WAITING_FOR_CHEST -> "message.questown.chicken.hint.chest";
            case WAITING_FOR_PRESSURE_PLATE -> "message.questown.chicken.hint.welcome_mat";
            case WAITING_FOR_VILLAGER_UI -> "message.questown.chicken.hint.villager_ui";
            case WAITING_FOR_FLAG_UI -> "message.questown.chicken.hint.flag_ui";
            case AWAITING_WORLDLY_SEEDS_DELIVERY -> "message.questown.chicken.hint.worldly_seeds";
            case COMPLETE, FORFEIT -> null;
        };
    }
}

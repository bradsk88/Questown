package ca.bradj.questown.mobs.helperchicken;

import ca.bradj.questown.core.init.items.ItemsInit;
import ca.bradj.questown.town.entity.TownFlagBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Helper-chicken entity used for the onboarding arc.
 *
 * <p>v1 behaviour: invulnerable, exposes three {@link SynchedEntityData} fields
 * for the speech-bubble layer (U2), and follows the player near the flag
 * while walking to beat-target blocks (U3).
 */
public class HelperChickenEntity extends Chicken {

    private static final EntityDataAccessor<ItemStack> BUBBLE_ICON_A = SynchedEntityData.defineId(
            HelperChickenEntity.class, EntityDataSerializers.ITEM_STACK
    );
    private static final EntityDataAccessor<ItemStack> BUBBLE_ICON_B = SynchedEntityData.defineId(
            HelperChickenEntity.class, EntityDataSerializers.ITEM_STACK
    );
    private static final EntityDataAccessor<Boolean> THROUGH_WALLS = SynchedEntityData.defineId(
            HelperChickenEntity.class, EntityDataSerializers.BOOLEAN
    );

    @Nullable
    private BlockPos ownerFlagPos;

    public HelperChickenEntity(
            EntityType<? extends Chicken> type,
            Level level
    ) {
        super(type, level);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        // Peck must outrank follow: both use MOVE+LOOK and the lower priority
        // number wins, so swapping them lets peck preempt follow whenever the
        // beat-peck goal's own gates pass (target resolved + player holds the
        // required item, or it's a UI beat with no item gate). Follow then
        // takes over only while peck stands down — which is what the player
        // expects: chicken trails them while they fetch the item, then walks
        // to the target and pecks once they have it.
        this.goalSelector.addGoal(2, new HelperChickenBeatPeckGoal(this, this::getOwnerFlagPos));
        this.goalSelector.addGoal(3, new HelperChickenFollowNearFlagGoal(this, 1.0D, this::getOwnerFlagPos));
        this.goalSelector.addGoal(5, new LookAtPlayerGoal(this, Player.class, 6.0F));
        this.goalSelector.addGoal(6, new RandomLookAroundGoal(this));
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(BUBBLE_ICON_A, ItemStack.EMPTY);
        this.entityData.define(BUBBLE_ICON_B, ItemStack.EMPTY);
        this.entityData.define(THROUGH_WALLS, false);
    }

    @Override
    public boolean hurt(
            DamageSource source,
            float amount
    ) {
        // v1 invulnerability — the helper chicken cannot be damaged by any source.
        // When a player attacks we convert the swing into a hint-text delivery
        // so the player gets positive feedback instead of a silent no-op.
        if (source.getEntity() instanceof ServerPlayer sp) {
            onPlayerLeftClick(sp);
        }
        return false;
    }

    /**
     * Forge calls this when the player right-clicks the chicken.
     *
     * <p>Two paths:
     * <ul>
     *   <li>Holding Worldly Seeds in the clicked hand AND beat state is
     *       {@link ChickenBeatState#AWAITING_WORLDLY_SEEDS_DELIVERY}: consume
     *       one seed, run the statue transform, end the arc.</li>
     *   <li>Anything else: dispatch to the hint-text path so the click
     *       surfaces the current beat's guidance without damage.</li>
     * </ul>
     */
    @Override
    public InteractionResult interactAt(
            Player player,
            Vec3 vec,
            InteractionHand hand
    ) {
        if (!(player instanceof ServerPlayer sp)) {
            return super.interactAt(player, vec, hand);
        }
        if (this.ownerFlagPos == null) {
            return super.interactAt(player, vec, hand);
        }
        if (!(this.level.getBlockEntity(this.ownerFlagPos) instanceof TownFlagBlockEntity flag)) {
            return super.interactAt(player, vec, hand);
        }
        ItemStack held = player.getItemInHand(hand);
        if (tryDeliverWorldlySeeds(sp, flag, held)) {
            return InteractionResult.sidedSuccess(this.level.isClientSide);
        }
        ChickenArcController.onPlayerClickedChicken(sp, flag);
        return InteractionResult.sidedSuccess(this.level.isClientSide);
    }

    private boolean tryDeliverWorldlySeeds(
            ServerPlayer player,
            TownFlagBlockEntity flag,
            ItemStack held
    ) {
        if (flag.getChickenBeatState() != ChickenBeatState.AWAITING_WORLDLY_SEEDS_DELIVERY) {
            return false;
        }
        if (!held.is(ItemsInit.WORLDLY_SEEDS.get())) {
            return false;
        }
        held.shrink(1);
        if (!player.getAbilities().instabuild) {
            player.getInventory().setChanged();
        }
        flag.setChickenObservedSeedsGiven(true);
        ChickenStatueTransformHandler.transform(this, flag);
        return true;
    }

    /**
     * Routes left-click (attack) to the hint-text handler. Damage is already
     * suppressed by {@link #hurt}; this adds the visible feedback so the player
     * sees the chicken "teach them" rather than silently ignoring the swing.
     */
    public void onPlayerLeftClick(ServerPlayer player) {
        if (this.ownerFlagPos == null) {
            return;
        }
        if (this.level.getBlockEntity(this.ownerFlagPos) instanceof TownFlagBlockEntity flag) {
            ChickenArcController.onPlayerClickedChicken(player, flag);
        }
    }

    public ItemStack getBubbleIconA() {
        return this.entityData.get(BUBBLE_ICON_A);
    }

    public void setBubbleIconA(ItemStack stack) {
        this.entityData.set(BUBBLE_ICON_A, stack);
    }

    public ItemStack getBubbleIconB() {
        return this.entityData.get(BUBBLE_ICON_B);
    }

    public void setBubbleIconB(ItemStack stack) {
        this.entityData.set(BUBBLE_ICON_B, stack);
    }

    public boolean isThroughWalls() {
        return this.entityData.get(THROUGH_WALLS);
    }

    public void setThroughWalls(boolean throughWalls) {
        this.entityData.set(THROUGH_WALLS, throughWalls);
    }

    /**
     * World-position of the owning flag BE. Set by
     * {@link ca.bradj.questown.town.HelperChickenSpawnController} at spawn time.
     * The AI goals look up the flag BE via {@code level.getBlockEntity(pos)} each
     * tick rather than holding a long-term reference.
     */
    @Nullable
    public BlockPos getOwnerFlagPos() {
        return this.ownerFlagPos;
    }

    public void setOwnerFlagPos(@Nullable BlockPos pos) {
        this.ownerFlagPos = pos;
    }

    /**
     * Resolves the current beat state from the owning flag BE, or returns
     * {@link ChickenBeatState#FORFEIT} if the flag BE cannot be found. Used by
     * the follow goal's arc-active check.
     */
    public ChickenBeatState getBeatStateSafe() {
        if (this.ownerFlagPos == null) {
            return ChickenBeatState.FORFEIT;
        }
        if (this.level.getBlockEntity(this.ownerFlagPos) instanceof TownFlagBlockEntity flag) {
            return flag.getChickenBeatState();
        }
        return ChickenBeatState.FORFEIT;
    }
}

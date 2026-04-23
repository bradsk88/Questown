package ca.bradj.questown.mobs.helperchicken;

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
        this.goalSelector.addGoal(2, new HelperChickenFollowNearFlagGoal(this, 1.0D, this::getOwnerFlagPos));
        this.goalSelector.addGoal(3, new HelperChickenBeatPeckGoal(this, this::getOwnerFlagPos));
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
     * Forge calls this when the player right-clicks the chicken. We dispatch to
     * the same hint-text path used by left-click so both clicks surface the
     * current beat's guidance without damage.
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
        if (this.level.getBlockEntity(this.ownerFlagPos) instanceof TownFlagBlockEntity flag) {
            ChickenArcController.onPlayerClickedChicken(sp, flag);
            return InteractionResult.sidedSuccess(this.level.isClientSide);
        }
        return super.interactAt(player, vec, hand);
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

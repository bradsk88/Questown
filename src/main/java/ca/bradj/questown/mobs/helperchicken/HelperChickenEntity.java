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
import net.minecraft.world.entity.ai.goal.OpenDoorGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** The helper chicken: can't be hurt, shows a speech bubble, and follows the player near the flag. */
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
    /** A texture to show in the bubble instead of the item icons, as a resource location string; empty for none. */
    private static final EntityDataAccessor<String> BUBBLE_TEXTURE_PATH = SynchedEntityData.defineId(
            HelperChickenEntity.class, EntityDataSerializers.STRING
    );
    /** The current hint's lang key, so the client can show it; empty when the lessons are over. */
    private static final EntityDataAccessor<String> HINT_KEY = SynchedEntityData.defineId(
            HelperChickenEntity.class, EntityDataSerializers.STRING
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
        // Let the chicken path through doors and open them.
        if (this.getNavigation() instanceof GroundPathNavigation gnav) {
            gnav.setCanOpenDoors(true);
            gnav.setCanPassDoors(true);
        }
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new OpenDoorGoal(this, true));
        // Pecking outranks following, so the chicken follows only while it has nowhere to peck.
        this.goalSelector.addGoal(2, new HelperChickenSunsetChestGoal(this, this::getOwnerFlagPos));
        this.goalSelector.addGoal(2, new HelperChickenRoomBuildGoal(this, this::getOwnerFlagPos));
        this.goalSelector.addGoal(2, new HelperChickenBeatPeckGoal(this, this::getOwnerFlagPos));
        this.goalSelector.addGoal(3, new HelperChickenFollowNearFlagGoal(this, 1.0D, this::getOwnerFlagPos));
        this.goalSelector.addGoal(3, new HelperChickenWanderNearFlagGoal(this, this::getOwnerFlagPos));
        this.goalSelector.addGoal(5, new LookAtPlayerGoal(this, Player.class, 6.0F));
        this.goalSelector.addGoal(6, new RandomLookAroundGoal(this));
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(BUBBLE_ICON_A, ItemStack.EMPTY);
        this.entityData.define(BUBBLE_ICON_B, ItemStack.EMPTY);
        this.entityData.define(THROUGH_WALLS, false);
        this.entityData.define(BUBBLE_TEXTURE_PATH, "");
        this.entityData.define(HINT_KEY, "");
    }

    @Override
    public boolean hurt(
            DamageSource source,
            float amount
    ) {
        // Can't be hurt: a hit shows the hint instead.
        if (source.getEntity() instanceof ServerPlayer sp) {
            onPlayerLeftClick(sp);
        }
        return false;
    }

    /** Right-click: with Worldly Seeds during the seeds lesson, ends the lessons; otherwise shows the hint. */
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
        if (!(this.level().getBlockEntity(this.ownerFlagPos) instanceof TownFlagBlockEntity flag)) {
            return super.interactAt(player, vec, hand);
        }
        ItemStack held = player.getItemInHand(hand);
        if (tryDeliverWorldlySeeds(sp, flag, held)) {
            return InteractionResult.sidedSuccess(this.level().isClientSide);
        }
        // Main hand only, so one click counts once.
        if (hand == InteractionHand.MAIN_HAND) {
            ChickenArcController.onPlayerClickedChicken(sp, flag);
        }
        return InteractionResult.sidedSuccess(this.level().isClientSide);
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

    /** A hit shows the hint. */
    public void onPlayerLeftClick(ServerPlayer player) {
        if (this.ownerFlagPos == null) {
            return;
        }
        if (this.level().getBlockEntity(this.ownerFlagPos) instanceof TownFlagBlockEntity flag) {
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

    public String getBubbleTexturePath() {
        return this.entityData.get(BUBBLE_TEXTURE_PATH);
    }

    public void setBubbleTexturePath(String path) {
        this.entityData.set(BUBBLE_TEXTURE_PATH, path == null ? "" : path);
    }

    public String getHintKey() {
        return this.entityData.get(HINT_KEY);
    }

    public void setHintKey(String key) {
        this.entityData.set(HINT_KEY, key == null ? "" : key);
    }

    /** The owning flag's position. */
    @Nullable
    public BlockPos getOwnerFlagPos() {
        return this.ownerFlagPos;
    }

    public void setOwnerFlagPos(@Nullable BlockPos pos) {
        this.ownerFlagPos = pos;
    }

    private static final String NBT_OWNER_FLAG_POS = "questown_owner_flag_pos";

    @Override
    public void addAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (this.ownerFlagPos != null) {
            tag.put(NBT_OWNER_FLAG_POS, net.minecraft.nbt.NbtUtils.writeBlockPos(this.ownerFlagPos));
        }
    }

    @Override
    public void readAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains(NBT_OWNER_FLAG_POS)) {
            this.ownerFlagPos = tag.contains(NBT_OWNER_FLAG_POS) ? net.minecraft.nbt.NbtUtils.readBlockPos(tag.getCompound(NBT_OWNER_FLAG_POS)) : null;
        }
    }

    /** The current lesson, or {@link ChickenBeatState#FORFEIT} when the flag is gone. */
    public ChickenBeatState getBeatStateSafe() {
        if (this.ownerFlagPos == null) {
            return ChickenBeatState.FORFEIT;
        }
        if (this.level().getBlockEntity(this.ownerFlagPos) instanceof TownFlagBlockEntity flag) {
            return flag.getChickenBeatState();
        }
        return ChickenBeatState.FORFEIT;
    }
}

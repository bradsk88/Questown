package ca.bradj.questown.mobs.helperchicken;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Helper-chicken entity used for the onboarding arc.
 *
 * <p>v1 behaviour: invulnerable, no AI goals (U3 populates them), and exposes
 * three {@link SynchedEntityData} fields for the speech-bubble layer (U2):
 * two item icons and a through-walls flag.
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

    public HelperChickenEntity(
            EntityType<? extends Chicken> type,
            Level level
    ) {
        super(type, level);
    }

    @Override
    protected void registerGoals() {
        // Intentionally left empty in U1 — AI goals are added in U3.
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
        return false;
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
}

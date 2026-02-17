package ca.bradj.questown.jobs.declarative;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Verifies assumptions about item damageability that the warp degradeTool logic depends on.
 *
 * TimeWarpWorldInteraction.degradeTool() skips degradation for non-damageable items
 * (isDamageableItem() == false). This matches the realtime path where hurtAndBreak()
 * is a no-op for such items. If these assumptions break, cook warp will destroy
 * food/fuel tools (see docs/bugs/warp-degrade-tool-destroys-food-items.md).
 */
class DegradeToolTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void foodItems_shouldNotBeDamageable() {
        Assertions.assertFalse(new ItemStack(Items.BEEF).isDamageableItem());
        Assertions.assertFalse(new ItemStack(Items.PORKCHOP).isDamageableItem());
        Assertions.assertFalse(new ItemStack(Items.CHICKEN).isDamageableItem());
        Assertions.assertFalse(new ItemStack(Items.MUTTON).isDamageableItem());
        Assertions.assertFalse(new ItemStack(Items.RABBIT).isDamageableItem());
        Assertions.assertFalse(new ItemStack(Items.POTATO).isDamageableItem());
    }

    @Test
    void fuelItems_shouldNotBeDamageable() {
        Assertions.assertFalse(new ItemStack(Items.COAL).isDamageableItem());
        Assertions.assertFalse(new ItemStack(Items.CHARCOAL).isDamageableItem());
        Assertions.assertFalse(new ItemStack(Items.STICK).isDamageableItem());
    }

    @Test
    void actualTools_shouldBeDamageable() {
        Assertions.assertTrue(new ItemStack(Items.WOODEN_HOE).isDamageableItem());
        Assertions.assertTrue(new ItemStack(Items.IRON_AXE).isDamageableItem());
        Assertions.assertTrue(new ItemStack(Items.STONE_PICKAXE).isDamageableItem());
    }

    @Test
    void nonDamageableItem_shouldReportZeroMaxDamage() {
        ItemStack beef = new ItemStack(Items.BEEF);
        Assertions.assertEquals(0, beef.getMaxDamage());
        // This is the condition that caused the original bug:
        // getDamageValue() >= getMaxDamage() → 0 >= 0 → true → item destroyed
        Assertions.assertTrue(
                beef.getDamageValue() >= beef.getMaxDamage(),
                "Non-damageable items satisfy the old broken condition (0 >= 0), " +
                "which is why the isDamageableItem() guard is needed"
        );
    }
}

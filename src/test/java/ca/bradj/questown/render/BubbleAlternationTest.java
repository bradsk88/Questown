package ca.bradj.questown.render;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Covers the pure icon-selection logic extracted from
 * {@link BubbleRenderer}. Rendering itself is not unit-testable
 * and is verified in-game per the U2 plan.
 */
class BubbleAlternationTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static ItemStack iconA() {
        return new ItemStack(Items.STICK);
    }

    private static ItemStack iconB() {
        return new ItemStack(Items.WHEAT_SEEDS);
    }

    @Test
    void alternation_tick0_showsA() {
        ItemStack a = iconA();
        ItemStack b = iconB();
        Assertions.assertSame(a, BubbleRenderer.chooseDisplayedIcon(0, a, b));
    }

    @Test
    void alternation_tick19_stillShowsA() {
        ItemStack a = iconA();
        ItemStack b = iconB();
        Assertions.assertSame(a, BubbleRenderer.chooseDisplayedIcon(19, a, b));
    }

    @Test
    void alternation_tick20_flipsToB() {
        ItemStack a = iconA();
        ItemStack b = iconB();
        Assertions.assertSame(b, BubbleRenderer.chooseDisplayedIcon(20, a, b));
    }

    @Test
    void alternation_tick39_stillShowsB() {
        ItemStack a = iconA();
        ItemStack b = iconB();
        Assertions.assertSame(b, BubbleRenderer.chooseDisplayedIcon(39, a, b));
    }

    @Test
    void alternation_tick40_flipsBackToA() {
        ItemStack a = iconA();
        ItemStack b = iconB();
        Assertions.assertSame(a, BubbleRenderer.chooseDisplayedIcon(40, a, b));
    }

    @Test
    void singleIconMode_tick0_showsA() {
        ItemStack a = iconA();
        Assertions.assertSame(a, BubbleRenderer.chooseDisplayedIcon(0, a, ItemStack.EMPTY));
    }

    @Test
    void singleIconMode_tick20_stillShowsA() {
        ItemStack a = iconA();
        Assertions.assertSame(a, BubbleRenderer.chooseDisplayedIcon(20, a, ItemStack.EMPTY));
    }

    @Test
    void bothEmpty_returnsEmpty() {
        ItemStack result = BubbleRenderer.chooseDisplayedIcon(
                0, ItemStack.EMPTY, ItemStack.EMPTY
        );
        Assertions.assertTrue(result.isEmpty());
    }
}

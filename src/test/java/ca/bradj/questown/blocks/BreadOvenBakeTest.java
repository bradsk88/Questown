package ca.bradj.questown.blocks;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Registry-free bake arithmetic for {@link BreadOvenBake} — the single source
 * of truth shared by the realtime {@link BreadOvenBlockEntity} and the warp
 * lane: 1 wheat + 1 coal -> 1 bread over {@link BreadOvenBake#BAKE_TICKS}
 * ticks, output stacks like a furnace, partial-progress reset on a slot
 * change. No level, no registry — just a slot list.
 */
class BreadOvenBakeTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        // #minecraft:coals ships in the vanilla datapack, which a unit test
        // never loads — bind the tag onto the coal/charcoal holders so
        // BreadOvenBake.isCoal resolves the way the game's tag registry
        // resolves it (1.20.6 checks ItemStack.is(tag) via the holder's
        // own TagKey set, populated by bindTags). Mirrors the job data's
        // #minecraft:coals. (BuiltInRegistries must NOT be touched
        // before bootStrap(): its clinit registers vanilla entries and
        // demands the bootstrap flag — a pre-bootstrap touch poisons the
        // whole forked JVM.)
        Items.COAL.builtInRegistryHolder().bindTags(Set.of(ItemTags.COALS));
        Items.CHARCOAL.builtInRegistryHolder().bindTags(Set.of(ItemTags.COALS));
    }

    private static ItemStack stack(Item item, int count) {
        return count > 0 ? new ItemStack(item, count) : ItemStack.EMPTY;
    }

    private static BreadOvenBake bake(int wheat, int coal, int bread) {
        List<ItemStack> slots = new ArrayList<>();
        slots.add(stack(Items.WHEAT, wheat));
        slots.add(stack(Items.COAL, coal));
        slots.add(stack(Items.BREAD, bread));
        return new BreadOvenBake(slots);
    }

    @Test
    void advanceOneTick_bakesBread_afterBAKETickTicks() {
        BreadOvenBake b = bake(1, 1, 0);
        for (int i = 0; i < BreadOvenBake.BAKE_TICKS - 1; i++) {
            Assertions.assertFalse(b.advanceOneTick(), "no completion before the final tick");
        }
        Assertions.assertTrue(b.getItem(BreadOvenBake.SLOT_BREAD).isEmpty(),
                "no bread before the final tick");
        Assertions.assertTrue(b.advanceOneTick(), "the final tick completes the bake");
        Assertions.assertEquals(Items.BREAD, b.getItem(BreadOvenBake.SLOT_BREAD).getItem());
        Assertions.assertEquals(1, b.getItem(BreadOvenBake.SLOT_BREAD).getCount());
        Assertions.assertTrue(b.getItem(BreadOvenBake.SLOT_WHEAT).isEmpty());
        Assertions.assertTrue(b.getItem(BreadOvenBake.SLOT_COAL).isEmpty());
    }

    @Test
    void advance_stacksBreadWhenSlotPartiallyOccupied() {
        BreadOvenBake b = bake(2, 2, 1);
        Assertions.assertTrue(b.advance(BreadOvenBake.BAKE_TICKS));
        Assertions.assertEquals(2, b.getItem(BreadOvenBake.SLOT_BREAD).getCount(),
                "bread should stack into a partially filled output slot");
    }

    @Test
    void canBake_rejectsFullOutputSlot() {
        BreadOvenBake b = bake(1, 1, 0);
        b.setItem(BreadOvenBake.SLOT_BREAD, new ItemStack(Items.BREAD, 64));
        Assertions.assertFalse(b.canBake(), "full output stack blocks the bake");
    }

    @Test
    void advance_multipleBakesStackUpToMax() {
        BreadOvenBake b = bake(64, 64, 0);
        for (int i = 0; i < 5; i++) {
            Assertions.assertTrue(b.advance(BreadOvenBake.BAKE_TICKS), "bake " + (i + 1) + " should complete");
        }
        Assertions.assertEquals(5, b.getItem(BreadOvenBake.SLOT_BREAD).getCount());
        Assertions.assertEquals(59, b.getItem(BreadOvenBake.SLOT_WHEAT).getCount());
        Assertions.assertEquals(59, b.getItem(BreadOvenBake.SLOT_COAL).getCount());
    }

    @Test
    void canBake_rejectsWrongItems() {
        Assertions.assertFalse(bake(1, 0, 0).canBake(), "no coal");
        Assertions.assertFalse(bake(0, 1, 0).canBake(), "no wheat");
        Assertions.assertFalse(bake(1, 1, 64).canBake(), "output full");
        BreadOvenBake wrongInput = bake(0, 1, 0);
        wrongInput.setItem(BreadOvenBake.SLOT_WHEAT, new ItemStack(Items.CARROT));
        Assertions.assertFalse(wrongInput.canBake(), "non-wheat input");
        BreadOvenBake wrongFuel = bake(0, 0, 0);
        wrongFuel.setItem(BreadOvenBake.SLOT_WHEAT, new ItemStack(Items.WHEAT));
        wrongFuel.setItem(BreadOvenBake.SLOT_COAL, new ItemStack(Items.GUNPOWDER));
        Assertions.assertFalse(wrongFuel.canBake(), "non-coal fuel");
    }

    @Test
    void takeBread_takesFromOutputSlot() {
        BreadOvenBake b = bake(0, 0, 3);
        ItemStack taken = b.takeBread(2);
        Assertions.assertNotNull(taken);
        Assertions.assertEquals(2, taken.getCount());
        Assertions.assertEquals(1, b.getItem(BreadOvenBake.SLOT_BREAD).getCount());
        ItemStack tooMany = b.takeBread(5);
        Assertions.assertNotNull(tooMany);
        Assertions.assertEquals(1, tooMany.getCount(), "takes only what the slot holds");
        Assertions.assertTrue(b.getItem(BreadOvenBake.SLOT_BREAD).isEmpty());
        Assertions.assertNull(b.takeBread(1), "an empty output slot returns null");
    }

    @Test
    void advance_resumesProgressAcrossCalls() {
        BreadOvenBake b = bake(1, 1, 0);
        b.advance(400);
        Assertions.assertTrue(b.getItem(BreadOvenBake.SLOT_BREAD).isEmpty(), "no bread at 400 ticks");
        Assertions.assertEquals(400, b.progress());
        b.advance(600);
        Assertions.assertEquals(1, b.getItem(BreadOvenBake.SLOT_BREAD).getCount(),
                "progress must carry across calls and complete");
        Assertions.assertEquals(0, b.progress());
    }

    @Test
    void advance_resetsProgressWhenASlotChanges() {
        BreadOvenBake b = bake(1, 1, 0);
        b.advance(999);
        Assertions.assertEquals(999, b.progress());
        b.setItem(BreadOvenBake.SLOT_WHEAT, ItemStack.EMPTY); // e.g. the wheat was pulled
        b.advance(BreadOvenBake.BAKE_TICKS);
        Assertions.assertTrue(b.getItem(BreadOvenBake.SLOT_BREAD).isEmpty(),
                "an interrupted bake must not complete");
        Assertions.assertEquals(0, b.progress(), "partial progress is discarded on interruption");
    }
}

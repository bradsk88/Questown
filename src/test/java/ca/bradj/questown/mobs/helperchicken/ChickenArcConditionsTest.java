package ca.bradj.questown.mobs.helperchicken;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * Covers the new item-in-hand gating helpers added for the "follow until
 * player has the item, then peck" behaviour. The predicate map itself is
 * private; these tests exercise it via {@link ChickenArcConditions#shouldPeckRun}
 * and {@link ChickenArcConditions#playerHoldsRequiredItem} which are the
 * public surface used by the bubble factory and the peck goal.
 *
 * <p>Mod-registered items (Town Wand, Worldly Seeds, Welcome Mat) are not
 * exercised here because their {@code RegistryObject.get()} returns null
 * outside the Forge mod-loading lifecycle. They are covered by chicken-arc
 * autotest scenarios in-game.
 */
class ChickenArcConditionsTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void shouldPeckRun_uiBeats_alwaysTrue_evenWithNullPlayer() {
        // UI beats have no held-item requirement: chicken pecks the campfire
        // regardless of what (or whether) the player holds.
        Assertions.assertTrue(ChickenArcConditions.shouldPeckRun(null, ChickenBeatState.WAITING_FOR_VILLAGER_UI));
        Assertions.assertTrue(ChickenArcConditions.shouldPeckRun(null, ChickenBeatState.WAITING_FOR_FLAG_UI));
    }

    @Test
    void shouldPeckRun_seedsDelivery_alwaysTrue() {
        // F4 seeds-delivery is unchanged by the new requirement — chicken
        // pecks whichever container holds the seeds, no item-in-hand gate.
        Assertions.assertTrue(ChickenArcConditions.shouldPeckRun(null, ChickenBeatState.AWAITING_WORLDLY_SEEDS_DELIVERY));
    }

    @Test
    void shouldPeckRun_terminals_returnTrue() {
        // COMPLETE/FORFEIT have no peck target so the gate is moot, but it
        // stays permissive — the false-gate semantic is reserved for "player
        // needs the item but doesn't have it".
        Assertions.assertTrue(ChickenArcConditions.shouldPeckRun(null, ChickenBeatState.COMPLETE));
        Assertions.assertTrue(ChickenArcConditions.shouldPeckRun(null, ChickenBeatState.FORFEIT));
    }

    @Test
    void shouldPeckRun_sunsetAndMap_requiresWand() {
        // Phase-3 SUNSET_AND_MAP (chest spawned + night) is when the chicken
        // walks to the lit campfire to demonstrate the wand-on-fire sleep
        // action. Gate behaves like other wand beats: peck stands down until
        // the player is holding the wand.
        Assertions.assertFalse(ChickenArcConditions.shouldPeckRun(null, ChickenBeatState.SUNSET_AND_MAP));
    }

    @Test
    void shouldPeckRun_itemBeat_nullPlayer_returnsFalse() {
        // Player must be present and hold the item; null player → follow-near-flag
        // goal picks up and the chicken trails the player until they fetch it.
        Assertions.assertFalse(ChickenArcConditions.shouldPeckRun(null, ChickenBeatState.WAITING_FOR_STICK));
        Assertions.assertFalse(ChickenArcConditions.shouldPeckRun(null, ChickenBeatState.WAITING_FOR_WALL_BLOCK));
        Assertions.assertFalse(ChickenArcConditions.shouldPeckRun(null, ChickenBeatState.WAITING_FOR_DOOR));
        Assertions.assertFalse(ChickenArcConditions.shouldPeckRun(null, ChickenBeatState.WAITING_FOR_SIGN));
        Assertions.assertFalse(ChickenArcConditions.shouldPeckRun(null, ChickenBeatState.WAITING_FOR_CHEST));
    }

    @Test
    void shouldPeckRun_stickBeat_playerHoldingStickInMainHand_returnsTrue() {
        Player player = Mockito.mock(Player.class);
        Mockito.when(player.getMainHandItem()).thenReturn(new ItemStack(Items.STICK));
        Mockito.when(player.getOffhandItem()).thenReturn(ItemStack.EMPTY);
        Assertions.assertTrue(ChickenArcConditions.shouldPeckRun(player, ChickenBeatState.WAITING_FOR_STICK));
    }

    @Test
    void shouldPeckRun_stickBeat_playerHoldingStickInOffhand_returnsTrue() {
        Player player = Mockito.mock(Player.class);
        Mockito.when(player.getMainHandItem()).thenReturn(ItemStack.EMPTY);
        Mockito.when(player.getOffhandItem()).thenReturn(new ItemStack(Items.STICK));
        Assertions.assertTrue(ChickenArcConditions.shouldPeckRun(player, ChickenBeatState.WAITING_FOR_STICK));
    }

    @Test
    void shouldPeckRun_stickBeat_playerHoldingDifferentItem_returnsFalse() {
        Player player = Mockito.mock(Player.class);
        Mockito.when(player.getMainHandItem()).thenReturn(new ItemStack(Items.COBBLESTONE));
        Mockito.when(player.getOffhandItem()).thenReturn(ItemStack.EMPTY);
        Assertions.assertFalse(ChickenArcConditions.shouldPeckRun(player, ChickenBeatState.WAITING_FOR_STICK));
    }

    @Test
    void shouldPeckRun_doorBeat_anyDoorVariantSatisfies() {
        // The door beat accepts any DoorBlock-backed item — oak today, birch
        // tomorrow, no need to anchor to a specific wood type.
        Player playerWithBirch = Mockito.mock(Player.class);
        Mockito.when(playerWithBirch.getMainHandItem()).thenReturn(new ItemStack(Items.BIRCH_DOOR));
        Mockito.when(playerWithBirch.getOffhandItem()).thenReturn(ItemStack.EMPTY);
        Assertions.assertTrue(ChickenArcConditions.shouldPeckRun(playerWithBirch, ChickenBeatState.WAITING_FOR_DOOR));

        Player playerWithSpruce = Mockito.mock(Player.class);
        Mockito.when(playerWithSpruce.getMainHandItem()).thenReturn(new ItemStack(Items.SPRUCE_DOOR));
        Mockito.when(playerWithSpruce.getOffhandItem()).thenReturn(ItemStack.EMPTY);
        Assertions.assertTrue(ChickenArcConditions.shouldPeckRun(playerWithSpruce, ChickenBeatState.WAITING_FOR_DOOR));
    }

    @Test
    void shouldPeckRun_signBeat_anySignVariantSatisfies() {
        Player player = Mockito.mock(Player.class);
        Mockito.when(player.getMainHandItem()).thenReturn(new ItemStack(Items.BIRCH_SIGN));
        Mockito.when(player.getOffhandItem()).thenReturn(ItemStack.EMPTY);
        Assertions.assertTrue(ChickenArcConditions.shouldPeckRun(player, ChickenBeatState.WAITING_FOR_SIGN));
    }

    @Test
    void shouldPeckRun_wallBeat_anySolidBlockSatisfies() {
        // Beat satisfaction is "any solid-render block placed" — the gate
        // mirrors that, accepting any solid BlockItem the player picks up.
        Player playerWithStone = Mockito.mock(Player.class);
        Mockito.when(playerWithStone.getMainHandItem()).thenReturn(new ItemStack(Items.STONE));
        Mockito.when(playerWithStone.getOffhandItem()).thenReturn(ItemStack.EMPTY);
        Assertions.assertTrue(ChickenArcConditions.shouldPeckRun(playerWithStone, ChickenBeatState.WAITING_FOR_WALL_BLOCK));

        Player playerWithCobble = Mockito.mock(Player.class);
        Mockito.when(playerWithCobble.getMainHandItem()).thenReturn(new ItemStack(Items.COBBLESTONE));
        Mockito.when(playerWithCobble.getOffhandItem()).thenReturn(ItemStack.EMPTY);
        Assertions.assertTrue(ChickenArcConditions.shouldPeckRun(playerWithCobble, ChickenBeatState.WAITING_FOR_WALL_BLOCK));
    }

    @Test
    void shouldPeckRun_wallBeat_nonBlockItem_returnsFalse() {
        // A stick is not a wall block.
        Player player = Mockito.mock(Player.class);
        Mockito.when(player.getMainHandItem()).thenReturn(new ItemStack(Items.STICK));
        Mockito.when(player.getOffhandItem()).thenReturn(ItemStack.EMPTY);
        Assertions.assertFalse(ChickenArcConditions.shouldPeckRun(player, ChickenBeatState.WAITING_FOR_WALL_BLOCK));
    }

    @Test
    void playerHoldsRequiredItem_uiBeats_returnFalse_evenWithItem() {
        // For UI beats there's no required item — the bubble must stay in
        // its single-icon shape, so playerHoldsRequiredItem is false here
        // even though shouldPeckRun is true.
        Player player = Mockito.mock(Player.class);
        Mockito.when(player.getMainHandItem()).thenReturn(new ItemStack(Items.STICK));
        Mockito.when(player.getOffhandItem()).thenReturn(ItemStack.EMPTY);
        Assertions.assertFalse(ChickenArcConditions.playerHoldsRequiredItem(player, ChickenBeatState.WAITING_FOR_VILLAGER_UI));
        Assertions.assertFalse(ChickenArcConditions.playerHoldsRequiredItem(player, ChickenBeatState.WAITING_FOR_FLAG_UI));
    }

    @Test
    void playerHoldsRequiredItem_stickBeat_matchesHand() {
        Player playerWith = Mockito.mock(Player.class);
        Mockito.when(playerWith.getMainHandItem()).thenReturn(new ItemStack(Items.STICK));
        Mockito.when(playerWith.getOffhandItem()).thenReturn(ItemStack.EMPTY);
        Assertions.assertTrue(ChickenArcConditions.playerHoldsRequiredItem(playerWith, ChickenBeatState.WAITING_FOR_STICK));

        Player playerWithout = Mockito.mock(Player.class);
        Mockito.when(playerWithout.getMainHandItem()).thenReturn(ItemStack.EMPTY);
        Mockito.when(playerWithout.getOffhandItem()).thenReturn(ItemStack.EMPTY);
        Assertions.assertFalse(ChickenArcConditions.playerHoldsRequiredItem(playerWithout, ChickenBeatState.WAITING_FOR_STICK));
    }
}

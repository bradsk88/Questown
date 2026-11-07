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

/** Which lessons the chicken pecks for, and whether the player holds the right item. Mod items need the mod loaded, so the autotests cover them. */
class ChickenArcConditionsTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void shouldPeckRun_uiBeats_alwaysTrue_evenWithNullPlayer() {
        // Lessons with no item: the chicken always pecks.
        Assertions.assertTrue(ChickenArcConditions.shouldPeckRun(null, ChickenBeatState.WAITING_FOR_VILLAGER_UI));
        Assertions.assertTrue(ChickenArcConditions.shouldPeckRun(null, ChickenBeatState.WAITING_FOR_FLAG_UI));
    }

    @Test
    void shouldPeckRun_seedsDelivery_alwaysTrue() {
        Assertions.assertTrue(ChickenArcConditions.shouldPeckRun(null, ChickenBeatState.AWAITING_WORLDLY_SEEDS_DELIVERY));
    }

    @Test
    void shouldPeckRun_signBeat_noHeldSignRequired() {
        // The sign lesson waits on the job board, not on a held sign.
        Assertions.assertTrue(ChickenArcConditions.shouldPeckRun(null, ChickenBeatState.WAITING_FOR_SIGN));
    }

    @Test
    void shouldPeckRun_terminals_returnTrue() {
        Assertions.assertTrue(ChickenArcConditions.shouldPeckRun(null, ChickenBeatState.COMPLETE));
        Assertions.assertTrue(ChickenArcConditions.shouldPeckRun(null, ChickenBeatState.FORFEIT));
    }

    @Test
    void shouldPeckRun_sunsetAndMap_requiresWand() {
        // At night after the chest drop, the chicken waits for the player to hold the wand.
        Assertions.assertFalse(ChickenArcConditions.shouldPeckRun(null, ChickenBeatState.SUNSET_AND_MAP));
    }

    @Test
    void shouldPeckRun_itemBeat_nullPlayer_returnsFalse() {
        // No player, or no item: the chicken follows instead.
        Assertions.assertFalse(ChickenArcConditions.shouldPeckRun(null, ChickenBeatState.WAITING_FOR_STICK));
        Assertions.assertFalse(ChickenArcConditions.shouldPeckRun(null, ChickenBeatState.WAITING_FOR_WALL_BLOCK));
        Assertions.assertFalse(ChickenArcConditions.shouldPeckRun(null, ChickenBeatState.WAITING_FOR_DOOR));
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
        // Any door counts.
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
        // Any solid block counts.
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
        Player player = Mockito.mock(Player.class);
        Mockito.when(player.getMainHandItem()).thenReturn(new ItemStack(Items.STICK));
        Mockito.when(player.getOffhandItem()).thenReturn(ItemStack.EMPTY);
        Assertions.assertFalse(ChickenArcConditions.shouldPeckRun(player, ChickenBeatState.WAITING_FOR_WALL_BLOCK));
    }

    @Test
    void playerHoldsRequiredItem_uiBeats_returnFalse_evenWithItem() {
        // Lessons with no item keep their single icon.
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

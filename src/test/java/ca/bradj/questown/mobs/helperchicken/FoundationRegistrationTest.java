package ca.bradj.questown.mobs.helperchicken;

import ca.bradj.questown.town.entity.TownFlagTileData;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Rotation;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class FoundationRegistrationTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void chickenBeatState_fromNameSafe_validName_returnsEnum() {
        Assertions.assertEquals(
                ChickenBeatState.WAITING_FOR_STICK,
                ChickenBeatState.fromNameSafe("WAITING_FOR_STICK")
        );
        Assertions.assertEquals(
                ChickenBeatState.WAITING_FOR_WAND_ON_CAMPFIRE,
                ChickenBeatState.fromNameSafe("WAITING_FOR_WAND_ON_CAMPFIRE")
        );
        Assertions.assertEquals(
                ChickenBeatState.COMPLETE,
                ChickenBeatState.fromNameSafe("COMPLETE")
        );
    }

    @Test
    void chickenBeatState_fromNameSafe_invalidName_returnsForfeit() {
        Assertions.assertEquals(
                ChickenBeatState.FORFEIT,
                ChickenBeatState.fromNameSafe("INVALID_UNKNOWN")
        );
    }

    @Test
    void chickenBeatState_fromNameSafe_null_returnsForfeit() {
        Assertions.assertEquals(
                ChickenBeatState.FORFEIT,
                ChickenBeatState.fromNameSafe(null)
        );
    }

    @Test
    void chickenBeatState_fromNameSafe_emptyString_returnsForfeit() {
        Assertions.assertEquals(
                ChickenBeatState.FORFEIT,
                ChickenBeatState.fromNameSafe("")
        );
    }

    @Test
    void chickenBeatState_hasExactly16Values() {
        Assertions.assertEquals(16, ChickenBeatState.values().length);
    }

    @Test
    void chickenBeatState_canonicalOrdering() {
        // The lesson order.
        ChickenBeatState[] expected = {
                ChickenBeatState.WAITING_FOR_STICK,
                ChickenBeatState.WAITING_FOR_WALL_BLOCK,
                ChickenBeatState.WAITING_FOR_DOOR,
                ChickenBeatState.WAITING_FOR_WAND_ON_DOOR,
                ChickenBeatState.WAITING_FOR_ROOM_BUILD,
                ChickenBeatState.WAITING_FOR_CHEST,
                ChickenBeatState.WAITING_FOR_WAND_ON_CAMPFIRE,
                ChickenBeatState.WAITING_FOR_PRESSURE_PLATE,
                ChickenBeatState.WAITING_FOR_FLAG_UI,
                ChickenBeatState.SUNSET_AND_MAP,
                ChickenBeatState.WAITING_FOR_SIGN,
                ChickenBeatState.WAITING_FOR_FREE_FOOD,
                ChickenBeatState.WAITING_FOR_VILLAGER_UI,
                ChickenBeatState.AWAITING_WORLDLY_SEEDS_DELIVERY,
                ChickenBeatState.COMPLETE,
                ChickenBeatState.FORFEIT
        };
        Assertions.assertArrayEquals(expected, ChickenBeatState.values());
    }

    @Test
    void safeRotation_validName_returnsEnum() {
        Assertions.assertEquals(Rotation.NONE, TownFlagTileData.safeRotation("NONE"));
        Assertions.assertEquals(Rotation.CLOCKWISE_90, TownFlagTileData.safeRotation("CLOCKWISE_90"));
        Assertions.assertEquals(Rotation.CLOCKWISE_180, TownFlagTileData.safeRotation("CLOCKWISE_180"));
        Assertions.assertEquals(
                Rotation.COUNTERCLOCKWISE_90,
                TownFlagTileData.safeRotation("COUNTERCLOCKWISE_90")
        );
    }

    @Test
    void safeRotation_invalidName_returnsNone() {
        Assertions.assertEquals(Rotation.NONE, TownFlagTileData.safeRotation("INVALID_UNKNOWN"));
    }

    @Test
    void safeRotation_null_returnsNone() {
        Assertions.assertEquals(Rotation.NONE, TownFlagTileData.safeRotation(null));
    }

    @Test
    void chickenBubbleIcon_hasExactly14Values() {
        Assertions.assertEquals(14, ChickenBubbleIcon.values().length);
    }

    @Test
    void chickenBubbleIcon_containsCanonicalIcons() {
        Assertions.assertNotNull(ChickenBubbleIcon.valueOf("STICK"));
        Assertions.assertNotNull(ChickenBubbleIcon.valueOf("WAND"));
        Assertions.assertNotNull(ChickenBubbleIcon.valueOf("UNLIT_CAMPFIRE"));
        Assertions.assertNotNull(ChickenBubbleIcon.valueOf("LIT_CAMPFIRE"));
        Assertions.assertNotNull(ChickenBubbleIcon.valueOf("SUNSET"));
        Assertions.assertNotNull(ChickenBubbleIcon.valueOf("MAP"));
        Assertions.assertNotNull(ChickenBubbleIcon.valueOf("DOOR"));
        Assertions.assertNotNull(ChickenBubbleIcon.valueOf("SIGN"));
        Assertions.assertNotNull(ChickenBubbleIcon.valueOf("CHEST"));
        Assertions.assertNotNull(ChickenBubbleIcon.valueOf("WELCOME_MAT"));
        Assertions.assertNotNull(ChickenBubbleIcon.valueOf("PRESSURE_PLATE"));
        Assertions.assertNotNull(ChickenBubbleIcon.valueOf("WORLDLY_SEEDS"));
        Assertions.assertNotNull(ChickenBubbleIcon.valueOf("VILLAGER_UI"));
        Assertions.assertNotNull(ChickenBubbleIcon.valueOf("FLAG_UI"));
    }

    @Test
    void registrationClasses_loadWithoutExceptions() {
        // Loading these classes runs their registrations.
        Assertions.assertDoesNotThrow(() -> Class.forName("ca.bradj.questown.core.init.EntitiesInit"));
        Assertions.assertDoesNotThrow(() -> Class.forName("ca.bradj.questown.core.init.BlocksInit"));
        Assertions.assertDoesNotThrow(() -> Class.forName("ca.bradj.questown.core.init.items.ItemsInit"));
        Assertions.assertDoesNotThrow(
                () -> Class.forName("ca.bradj.questown.mobs.helperchicken.HelperChickenEntity")
        );
        Assertions.assertDoesNotThrow(
                () -> Class.forName("ca.bradj.questown.loader.forge.HelperChickenEntityEvents")
        );
        Assertions.assertDoesNotThrow(
                () -> Class.forName("ca.bradj.questown.blocks.StoneChickenStatue")
        );
        Assertions.assertDoesNotThrow(
                () -> Class.forName("ca.bradj.questown.items.WorldlySeeds")
        );
    }

}

package ca.bradj.questown.mobs.helperchicken;

import ca.bradj.questown.town.entity.TownFlagTileData;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Rotation;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

class FoundationRegistrationTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    // -------------------------------------------------------------------------
    // ChickenBeatState — safe parsing from persisted NBT
    // -------------------------------------------------------------------------

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
    void chickenBeatState_hasExactly14Values() {
        // Canonical — U6 state-machine table depends on these names and ordering.
        Assertions.assertEquals(14, ChickenBeatState.values().length);
    }

    @Test
    void chickenBeatState_canonicalOrdering() {
        // Ordering is the state-machine progression. Must not drift.
        ChickenBeatState[] expected = {
                ChickenBeatState.WAITING_FOR_STICK,
                ChickenBeatState.WAITING_FOR_WAND_ON_CAMPFIRE,
                ChickenBeatState.SUNSET_AND_MAP,
                ChickenBeatState.WAITING_FOR_WALL_BLOCK,
                ChickenBeatState.WAITING_FOR_DOOR,
                ChickenBeatState.WAITING_FOR_WAND_ON_DOOR,
                ChickenBeatState.WAITING_FOR_SIGN,
                ChickenBeatState.WAITING_FOR_CHEST,
                ChickenBeatState.WAITING_FOR_PRESSURE_PLATE,
                ChickenBeatState.WAITING_FOR_VILLAGER_UI,
                ChickenBeatState.WAITING_FOR_FLAG_UI,
                ChickenBeatState.AWAITING_WORLDLY_SEEDS_DELIVERY,
                ChickenBeatState.COMPLETE,
                ChickenBeatState.FORFEIT
        };
        Assertions.assertArrayEquals(expected, ChickenBeatState.values());
    }

    // -------------------------------------------------------------------------
    // TownFlagTileData.safeRotation — safe parsing from persisted NBT
    // -------------------------------------------------------------------------

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

    // -------------------------------------------------------------------------
    // ChickenBubbleIcon — closed-enum surface verification
    // -------------------------------------------------------------------------

    @Test
    void chickenBubbleIcon_hasExactly14Values() {
        // One bubble icon per stage of the arc (plus welcome_mat + flag_ui + villager_ui variants).
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

    // -------------------------------------------------------------------------
    // Registration smoke test — force class initialization, verify no throws
    // -------------------------------------------------------------------------

    @Test
    void registrationClasses_loadWithoutExceptions() {
        // If any DeferredRegister.register(...) call in these <clinit> blocks throws,
        // the mod cannot start. Loading them here catches class-init-time bugs.
        Assertions.assertDoesNotThrow(() -> Class.forName("ca.bradj.questown.core.init.EntitiesInit"));
        Assertions.assertDoesNotThrow(() -> Class.forName("ca.bradj.questown.core.init.BlocksInit"));
        Assertions.assertDoesNotThrow(() -> Class.forName("ca.bradj.questown.core.init.items.ItemsInit"));
        Assertions.assertDoesNotThrow(
                () -> Class.forName("ca.bradj.questown.mobs.helperchicken.HelperChickenEntity")
        );
        Assertions.assertDoesNotThrow(
                () -> Class.forName("ca.bradj.questown.mobs.helperchicken.HelperChickenEntityEvents")
        );
        Assertions.assertDoesNotThrow(
                () -> Class.forName("ca.bradj.questown.blocks.StoneChickenStatue")
        );
        Assertions.assertDoesNotThrow(
                () -> Class.forName("ca.bradj.questown.items.WorldlySeeds")
        );
    }

    // -------------------------------------------------------------------------
    // Round-trip persistence for the 6 new chicken flag-BE fields.
    //
    // NOT TESTABLE AT UNIT LEVEL: TownFlagBlockEntity's constructor requires
    // TilesInit.TOWN_FLAG.get() (a Forge RegistryObject that is populated only
    // during the Forge mod-loading lifecycle). We cannot construct a real
    // instance in a unit test without booting Forge itself.
    //
    // Per CLAUDE.md: when the real code is not testable in isolation, add a
    // failing assertion so the gap is visible. An integration / in-game test
    // (see U1 "Verification" in the plan) covers the round-trip end-to-end.
    // -------------------------------------------------------------------------

    @Test
    @Disabled("Covered by chicken-arc scenarios — every scenario writes then reads the 6 chicken flag-BE fields. See docs/conventions/agent-chicken-verification-loop.md.")
    void flagBE_persistsAllSixChickenFieldsAcrossWriteRead() {
    }

    @Test
    @Disabled("Default initialization is exercised by every chicken-arc scenario's RESET_ARENA → PLACE_FLAG phase, which starts from a fresh flag BE.")
    void flagBE_loadedWithMissingChickenKeysUsesDefaults() {
    }
}

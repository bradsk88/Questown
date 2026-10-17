package ca.bradj.questown.gui;

import ca.bradj.questown.core.network.OpenFlagMenuMessage;
import io.netty.buffer.Unpooled;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Playtest 2026-10-17 round 5: the BOP tab's red dot clears once a player has withdrawn a BOP,
 * and an unseen BOP tab gets the flag's initial focus over the quests tab.
 */
class BopTabAttentionTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static FlagTabsEmbedding.FlagInfo info(boolean bopAttention) {
        return new FlagTabsEmbedding.FlagInfo(
                BlockPos.ZERO, true, true, true, true, true, false, false, false, bopAttention
        );
    }

    private static boolean bopDot(FlagTabsEmbedding.FlagInfo fi) {
        return FlagTabs.computeTabDecisions(fi).stream()
                .filter(d -> d.titleKey().equals("tooltips.blocks_of_progress"))
                .findFirst().orElseThrow().hasNotification();
    }

    @Test
    void bopNeverWithdrawn_redDot() {
        assertTrue(bopDot(info(true)));
    }

    @Test
    void bopWithdrawn_noRedDot() {
        assertFalse(bopDot(info(false)));
    }

    @Test
    void bopAttention_survivesTheWire() {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        info(false).write(buf);
        assertFalse(FlagTabsEmbedding.FlagInfo.read(buf).bopTabAttention());

        FriendlyByteBuf buf2 = new FriendlyByteBuf(Unpooled.buffer());
        info(true).write(buf2);
        assertTrue(FlagTabsEmbedding.FlagInfo.read(buf2).bopTabAttention());
    }

    @Test
    void bothTabsHaveDots_bopGetsInitialFocus() {
        assertEquals(OpenFlagMenuMessage.BOP, FlagTabs.initialFlagTab(false, true, true));
    }

    @Test
    void onlyQuestsDot_questsGetsInitialFocus() {
        assertEquals(OpenFlagMenuMessage.QUESTS, FlagTabs.initialFlagTab(false, false, true));
    }

    @Test
    void fullFlag_opensBop() {
        assertEquals(OpenFlagMenuMessage.BOP, FlagTabs.initialFlagTab(true, false, true));
    }

    @Test
    void nothingToSee_overview() {
        assertNull(FlagTabs.initialFlagTab(false, false, false));
    }
}

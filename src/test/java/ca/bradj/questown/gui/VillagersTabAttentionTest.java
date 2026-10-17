package ca.bradj.questown.gui;

import io.netty.buffer.Unpooled;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The flag's villagers tab carries a red dot from the first villager's arrival until a player
 * first opens that tab (playtest 2026-10-17).
 */
class VillagersTabAttentionTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static FlagTabsEmbedding.FlagInfo info(boolean showVillagers, boolean attention) {
        return new FlagTabsEmbedding.FlagInfo(
                BlockPos.ZERO, showVillagers, true, showVillagers, false, false, false, false, attention
        );
    }

    private static boolean villagersDot(FlagTabsEmbedding.FlagInfo fi) {
        return FlagTabs.computeTabDecisions(fi).stream()
                .filter(d -> d.titleKey().equals("tooltips.villagers"))
                .findFirst().orElseThrow().hasNotification();
    }

    @Test
    void noVillagersYet_noAttention() {
        assertFalse(FlagTabsEmbedding.FlagInfo.villagersTabAttention(false, false));
    }

    @Test
    void villagerArrivedAndTabNeverOpened_attention() {
        assertTrue(FlagTabsEmbedding.FlagInfo.villagersTabAttention(true, false));
    }

    @Test
    void tabAlreadyOpened_noAttention() {
        assertFalse(FlagTabsEmbedding.FlagInfo.villagersTabAttention(true, true));
    }

    @Test
    void attention_drivesVillagersTabRedDot() {
        assertTrue(villagersDot(info(true, true)));
        assertFalse(villagersDot(info(true, false)));
    }

    @Test
    void attention_survivesTheWire() {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        info(true, true).write(buf);
        assertTrue(FlagTabsEmbedding.FlagInfo.read(buf).villagersTabAttention());

        FriendlyByteBuf buf2 = new FriendlyByteBuf(Unpooled.buffer());
        info(true, false).write(buf2);
        assertFalse(FlagTabsEmbedding.FlagInfo.read(buf2).villagersTabAttention());
    }
}

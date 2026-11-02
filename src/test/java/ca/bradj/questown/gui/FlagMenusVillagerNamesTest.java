package ca.bradj.questown.gui;

import ca.bradj.questown.core.UtilClean;
import com.google.common.collect.ImmutableList;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import io.netty.buffer.Unpooled;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/** Quest-card tooltips carry "Villager N" names over the network, in the order {@code FlagMenus.fromNetwork} reads them. */
class FlagMenusVillagerNamesTest {

    private static final UUID A = UUID.randomUUID();
    private static final UUID B = UUID.randomUUID();

    @Test
    void flagMenuPayload_villagerNames_roundTrip() {
        BlockPos pos = new BlockPos(1, 64, 3);
        Map<UUID, String> names = Map.of(A, "Villager 1", B, "Villager 2");
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        TownQuestsContainer.write(buf, ImmutableList.of(), pos, QuestScreenSummary.NONE, names);
        TownBlockofProgressMenu.write(buf, FlagTabsEmbedding.FlagInfo.dumb(pos, true, false),
                ImmutableList.of());

        Collection<UIQuest> quests = VillagerQuestsContainer.readQuests(buf);
        BlockPos flagPos = VillagerQuestsContainer.readFlagPos(buf);
        QuestScreenSummary summary =
                new QuestScreenSummary(buf.readBoolean(), buf.readBoolean());
        Map<UUID, String> back = AbstractQuestsContainer.readVillagerNames(buf);
        TownBlockofProgressMenu.ReadResult bop = TownBlockofProgressMenu.readWithFlagInfo(buf);

        Assertions.assertTrue(quests.isEmpty());
        Assertions.assertEquals(pos, flagPos);
        Assertions.assertEquals(QuestScreenSummary.NONE, summary);
        Assertions.assertEquals(names, back);
        Assertions.assertEquals(pos, bop.flagInfo().flagPos());
    }

    @Test
    void flagMenuPayload_emptyNames_roundTrip() {
        BlockPos pos = new BlockPos(0, 0, 0);
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        TownQuestsContainer.write(buf, ImmutableList.of(), pos, QuestScreenSummary.NONE, Map.of());
        TownBlockofProgressMenu.write(buf, FlagTabsEmbedding.FlagInfo.dumb(pos, false, false),
                ImmutableList.of());
        VillagerQuestsContainer.readQuests(buf);
        VillagerQuestsContainer.readFlagPos(buf);
        new QuestScreenSummary(buf.readBoolean(), buf.readBoolean());
        Map<UUID, String> back = AbstractQuestsContainer.readVillagerNames(buf);
        TownBlockofProgressMenu.readWithFlagInfo(buf);
        Assertions.assertTrue(back.isEmpty());
    }

    @Test
    void villagerLabelFor_displayNameWins_thenTruncatedUuid() {
        Map<UUID, String> names = Map.of(A, "Villager 7");
        Assertions.assertEquals("Villager 7",
                AbstractQuestsContainer.villagerLabelFor(names, A.toString()));

        // No name sent: the shortened UUID.
        String truncated = UtilClean.truncateMiddle(A.toString());
        Assertions.assertNotEquals(A.toString(), truncated);
        Assertions.assertEquals(truncated,
                AbstractQuestsContainer.villagerLabelFor(Map.of(), A.toString()));

        // A string that isn't a UUID is shortened, not thrown.
        Assertions.assertEquals(UtilClean.truncateMiddle("garbage"),
                AbstractQuestsContainer.villagerLabelFor(names, "garbage"));
    }
}

package ca.bradj.questown.gui;

import com.google.common.collect.ImmutableList;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class TabNotificationTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private FlagTabsEmbedding.FlagInfo infoWithIncompleteQuests(boolean showBop) {
        return FlagTabsEmbedding.FlagInfo.withQuestNotification(BlockPos.ZERO, showBop, true);
    }

    private FlagTabsEmbedding.FlagInfo infoAllComplete(boolean showBop) {
        return FlagTabsEmbedding.FlagInfo.withQuestNotification(BlockPos.ZERO, showBop, false);
    }

    private Optional<FlagTabs.TabDecision> findTab(ImmutableList<FlagTabs.TabDecision> decisions, String titleKey) {
        return decisions.stream().filter(d -> d.titleKey().equals(titleKey)).findFirst();
    }

    @Test
    void whenHasIncompleteQuests_questsTabHasRedDot() {
        ImmutableList<FlagTabs.TabDecision> decisions = FlagTabs.computeTabDecisions(infoWithIncompleteQuests(false));
        Optional<FlagTabs.TabDecision> quests = findTab(decisions, "tooltips.quests");
        assertTrue(quests.isPresent());
        assertTrue(quests.get().hasNotification());
    }

    @Test
    void whenAllQuestsComplete_questsTabNoRedDot() {
        ImmutableList<FlagTabs.TabDecision> decisions = FlagTabs.computeTabDecisions(infoAllComplete(false));
        Optional<FlagTabs.TabDecision> quests = findTab(decisions, "tooltips.quests");
        assertTrue(quests.isPresent());
        assertFalse(quests.get().hasNotification());
    }

    @Test
    void bopTabAlwaysHasRedDot() {
        ImmutableList<FlagTabs.TabDecision> decisions = FlagTabs.computeTabDecisions(infoAllComplete(true));
        Optional<FlagTabs.TabDecision> bop = findTab(decisions, "tooltips.blocks_of_progress");
        assertTrue(bop.isPresent());
        assertTrue(bop.get().hasNotification());
    }

    @Test
    void whenBopCountZero_bopTabNotVisible() {
        ImmutableList<FlagTabs.TabDecision> decisions = FlagTabs.computeTabDecisions(infoAllComplete(false));
        Optional<FlagTabs.TabDecision> bop = findTab(decisions, "tooltips.blocks_of_progress");
        assertTrue(bop.isPresent());
        assertFalse(bop.get().visible());
    }

    @Test
    void whenBopCountPositive_bopTabVisible() {
        ImmutableList<FlagTabs.TabDecision> decisions = FlagTabs.computeTabDecisions(infoAllComplete(true));
        Optional<FlagTabs.TabDecision> bop = findTab(decisions, "tooltips.blocks_of_progress");
        assertTrue(bop.isPresent());
        assertTrue(bop.get().visible());
    }

    @Test
    void villagersTabNeverHasRedDot() {
        ImmutableList<FlagTabs.TabDecision> decisions = FlagTabs.computeTabDecisions(infoWithIncompleteQuests(true));
        Optional<FlagTabs.TabDecision> villagers = findTab(decisions, "tooltips.villagers");
        assertTrue(villagers.isPresent());
        assertFalse(villagers.get().hasNotification());
    }

    @Test
    void economicsTabNeverHasRedDot() {
        ImmutableList<FlagTabs.TabDecision> decisions = FlagTabs.computeTabDecisions(infoWithIncompleteQuests(true));
        Optional<FlagTabs.TabDecision> econ = findTab(decisions, "tooltips.economics");
        assertTrue(econ.isPresent());
        assertFalse(econ.get().hasNotification());
    }

    @Test
    void craftingTabNeverHasRedDot() {
        ImmutableList<FlagTabs.TabDecision> decisions = FlagTabs.computeTabDecisions(infoWithIncompleteQuests(true));
        Optional<FlagTabs.TabDecision> crafting = findTab(decisions, "tooltips.crafting");
        assertTrue(crafting.isPresent());
        assertFalse(crafting.get().hasNotification());
    }

    @Test
    void villagersAndQuestsAndEconTabsAlwaysVisible() {
        ImmutableList<FlagTabs.TabDecision> decisions = FlagTabs.computeTabDecisions(infoAllComplete(false));
        assertTrue(findTab(decisions, "tooltips.villagers").get().visible());
        assertTrue(findTab(decisions, "tooltips.quests").get().visible());
        assertTrue(findTab(decisions, "tooltips.economics").get().visible());
        assertTrue(findTab(decisions, "tooltips.crafting").get().visible());
    }

    @Test
    void fromTownState_withIncompleteQuests_hasNotification() {
        FlagTabsEmbedding.FlagInfo fi = FlagTabsEmbedding.FlagInfo.withQuestNotification(
                BlockPos.ZERO, true, true
        );
        assertTrue(fi.hasIncompleteQuests());
    }

    @Test
    void fromTownState_withAllComplete_noNotification() {
        FlagTabsEmbedding.FlagInfo fi = FlagTabsEmbedding.FlagInfo.withQuestNotification(
                BlockPos.ZERO, true, false
        );
        assertFalse(fi.hasIncompleteQuests());
    }

    @Test
    void fromTownState_withBop_showsBopTab() {
        FlagTabsEmbedding.FlagInfo fi = FlagTabsEmbedding.FlagInfo.withQuestNotification(
                BlockPos.ZERO, true, false
        );
        assertTrue(fi.showBlockOfProgressTab());
    }
}

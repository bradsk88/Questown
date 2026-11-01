package ca.bradj.questown.gui;

import ca.bradj.questown.jobs.JobID;
import ca.bradj.questown.town.quests.Quest;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The job-change card names the profession via its jobs.<root> key, not the bare job id ("Villager
 * job: simple_furnace_food"): nothing raw reaches the player.
 */
public class UIQuestJobChangeCardNameTest {

    @Test
    public void job_change_card_names_the_profession() {
        UIQuest q = new UIQuest(
                UUID.randomUUID(),
                JobID.toRL(new JobID("cook", "simple_furnace_food")),
                Quest.QuestType.JOB_CHANGE,
                null,
                Quest.QuestStatus.ACTIVE,
                null,
                null,
                null,
                null,
                null
        );
        TranslatableContents card = (TranslatableContents) q.getName().getContents();
        assertEquals("questown.menu.quests.job_change", card.getKey());
        Object arg = card.getArgs()[0];
        assertTrue(arg instanceof Component, "card arg is a raw " + arg.getClass().getSimpleName() + ": " + arg);
        assertEquals("jobs.cook", ((TranslatableContents) ((Component) arg).getContents()).getKey());
    }
}

package ca.bradj.questown.town.entity;

import ca.bradj.questown.jobs.JobID;
import ca.bradj.questown.town.quests.Quest;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/** A completed job-change quest's chat line uses the card's wording and the job's name. */
class TownMessagesQuestNameTest {

    @Test
    void jobChangeQuest_isNamedLikeTheQuestCard() {
        Component name = TownMessages.questName(
                Quest.QuestType.JOB_CHANGE, JobID.toRL(new JobID("hunter", "sword")), 1
        );

        TranslatableContents c = (TranslatableContents) name.getContents();
        Assertions.assertEquals("questown.menu.quests.job_change", c.getKey());
        TranslatableContents job = (TranslatableContents) ((Component) c.getArgs()[0]).getContents();
        Assertions.assertEquals("jobs.hunter", job.getKey());
    }

    @Test
    void jobUnlockQuest_namesTheJobByItsTranslatedName() {
        Component name = TownMessages.questName(
                Quest.QuestType.JOB_UNLOCK, JobID.toRL(new JobID("cook", "extract")), 1
        );

        TranslatableContents c = (TranslatableContents) name.getContents();
        Assertions.assertEquals("questown.menu.quests.job_unlock", c.getKey());
        TranslatableContents job = (TranslatableContents) ((Component) c.getArgs()[0]).getContents();
        Assertions.assertEquals("jobs.cook", job.getKey());
    }
}

package ca.bradj.questown.town.quests;

import ca.bradj.questown.jobs.JobID;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;
import java.util.function.ToIntFunction;

/**
 * Which open named-skill quests show as a blank card (dev ruling 2026-10-26, Outfitter #20): while
 * no villager in the skill's job is at the skill's level, the card names neither the skill nor its
 * item and its tooltip gives the level needed. It fills in once a villager reaches it.
 */
public final class SkillQuestLocks {

    private SkillQuestLocks() {
    }

    /**
     * @param openNamedSkills    open SKILL_UNLOCK quest id -> the skill it names
     * @param levelGate          the skill's {@code available_at_level}
     * @param highestLevelByRoot job root -> the highest level among the town's villagers in it
     * @return quest id -> the level needed, for each quest that is still locked
     */
    public static Map<ResourceLocation, Integer> lockedLevels(
            Map<ResourceLocation, JobID> openNamedSkills,
            ToIntFunction<JobID> levelGate,
            Map<String, Integer> highestLevelByRoot
    ) {
        Map<ResourceLocation, Integer> out = new HashMap<>();
        openNamedSkills.forEach((questId, skill) -> {
            int gate = levelGate.applyAsInt(skill);
            if (highestLevelByRoot.getOrDefault(skill.rootId(), 0) < gate) {
                out.put(questId, gate);
            }
        });
        return out;
    }
}

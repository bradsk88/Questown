package ca.bradj.questown.town.quests;

import ca.bradj.questown.jobs.JobID;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;
import java.util.function.ToIntFunction;

/** Which open named-skill quests show as a blank card: until a villager in the skill's job reaches its level. */
public final class SkillQuestLocks {

    private SkillQuestLocks() {
    }

    /**
     * @param openNamedSkills    each open skill quest's id and the skill it names
     * @param levelGate          the skill's {@code available_at_level}
     * @param highestLevelByRoot each job root's highest villager level
     * @return each still-locked quest's id and the level it needs
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

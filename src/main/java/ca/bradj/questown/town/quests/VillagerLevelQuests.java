package ca.bradj.questown.town.quests;

import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.Optional;

/**
 * A "train a villager" quest: it completes once any villager working a job root
 * reaches a level. A curated path posts one ahead of a batch whose skills need that level, so the
 * batch only appears once someone can learn them. Skill unlocks are per villager, so "any villager
 * of the root" is the one the player then spends the Block of Progress on. Wanted id
 * {@code questown:villager_level/<root>/<level>}.
 */
public final class VillagerLevelQuests {

    static final String PREFIX = "villager_level/";

    private VillagerLevelQuests() {
    }

    public record Target(String root, int level) {
    }

    public static String path(String root, int level) {
        return PREFIX + root + "/" + level;
    }

    /** The root and level a villager-level quest id names; empty for any other id. */
    public static Optional<Target> target(ResourceLocation id) {
        String path = id.getPath();
        if (!path.startsWith(PREFIX)) {
            return Optional.empty();
        }
        String rest = path.substring(PREFIX.length());
        int slash = rest.lastIndexOf('/');
        if (slash <= 0) {
            return Optional.empty();
        }
        try {
            return Optional.of(new Target(rest.substring(0, slash), Integer.parseInt(rest.substring(slash + 1))));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    /** @param highestLevelByRoot job root -> the highest level among the town's villagers in it */
    public static boolean met(Target t, Map<String, Integer> highestLevelByRoot) {
        return highestLevelByRoot.getOrDefault(t.root(), 0) >= t.level();
    }
}

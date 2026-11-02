package ca.bradj.questown.town.quests;

import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.Optional;

/** "Train a villager" quests: done once any villager in a job root reaches a level. Id {@code questown:villager_level/<root>/<level>}. */
public final class VillagerLevelQuests {

    static final String PREFIX = "villager_level/";

    private VillagerLevelQuests() {
    }

    public record Target(String root, int level) {
    }

    public static String path(String root, int level) {
        return PREFIX + root + "/" + level;
    }

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

    /** @param highestLevelByRoot each job root's highest villager level */
    public static boolean met(Target t, Map<String, Integer> highestLevelByRoot) {
        return highestLevelByRoot.getOrDefault(t.root(), 0) >= t.level();
    }
}

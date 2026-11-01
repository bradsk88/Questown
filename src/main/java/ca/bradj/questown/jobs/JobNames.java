package ca.bradj.questown.jobs;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * A readable fallback name for a job variant ("rod_half_day" -> "Rod (half day)") for the skill
 * tree's nodes and the "Learn More" job pages. A {@code jobs.<root>.<job>} lang key, when present,
 * wins over it.
 */
public final class JobNames {

    private JobNames() {
    }

    /**
     * The item a product job is named for: its icon, when the icon's path is the job id
     * ({@code crafter/wooden_shovel} -> {@code minecraft:wooden_shovel}). Its name then reads like
     * the item's tooltip, "Wooden Shovel". Leaver
     * variants and helper jobs ({@code hunter/sword_half_day}, {@code cook/fuel}) have none and
     * keep {@link #variant}.
     */
    public static @Nullable ResourceLocation product(JobID job, @Nullable ResourceLocation icon) {
        if (icon == null || !icon.getPath().equals(job.jobId())) {
            return null;
        }
        return icon;
    }

    public static String variant(String jobId) {
        List<String> words = new ArrayList<>(Arrays.asList(jobId.toLowerCase(Locale.ROOT).split("_")));
        String length = tripLength(words);
        String name = String.join(" ", words);
        if (name.isEmpty()) {
            name = jobId;
        }
        name = Character.toUpperCase(name.charAt(0)) + name.substring(1);
        return length == null ? name : name + " (" + length + ")";
    }

    /** Removes a trailing trip-length marker from {@code words} and returns its reading. */
    private static String tripLength(List<String> words) {
        int n = words.size();
        if (n >= 3 && words.get(n - 1).equals("day") && (words.get(n - 2).equals("half") || words.get(n - 2).equals("full"))) {
            String half = words.get(n - 2);
            words.subList(n - 2, n).clear();
            return half + " day";
        }
        if (n < 2) {
            return null;
        }
        String reading = switch (words.get(n - 1)) {
            case "short" -> "short trip";
            case "med" -> "medium trip";
            case "half" -> "half day";
            case "full" -> "full day";
            default -> null;
        };
        if (reading != null) {
            words.remove(n - 1);
        }
        return reading;
    }
}

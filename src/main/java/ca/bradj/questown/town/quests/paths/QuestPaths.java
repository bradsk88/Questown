package ca.bradj.questown.town.quests.paths;

import ca.bradj.questown.QT;
import com.google.common.collect.ImmutableList;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.InactiveProfiler;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.List;
import java.util.Map;

/**
 * The loaded curated paths, from {@code data/<ns>/questown_paths/*.json}. Loaded at server start
 * beside the jobs ({@code JobFileEvents}). A file that fails to parse or validate is skipped with
 * an error, so one bad path never takes the others down.
 */
public final class QuestPaths {

    private static volatile ImmutableList<QuestPath> loaded = ImmutableList.of();

    public static final Listener LISTENER = new Listener();

    private QuestPaths() {
    }

    public static ImmutableList<QuestPath> all() {
        return loaded;
    }

    public static class Listener extends SimpleJsonResourceReloadListener {

        protected Listener() {
            super(new Gson(), "questown_paths");
        }

        public void loadFromFiles(ResourceManager man) {
            apply(prepare(man, InactiveProfiler.INSTANCE), man, InactiveProfiler.INSTANCE);
        }

        @Override
        protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager man, ProfilerFiller profiler) {
            ImmutableList.Builder<QuestPath> b = ImmutableList.builder();
            for (Map.Entry<ResourceLocation, JsonElement> e : files.entrySet()) {
                try {
                    QuestPath path = QuestPathParser.parse(e.getValue().getAsJsonObject());
                    List<String> errors = QuestPathValidator.errors(path);
                    if (!errors.isEmpty()) {
                        QT.QUESTS_LOGGER.error("Skipping quest path {}: {}", e.getKey(), errors);
                        continue;
                    }
                    b.add(path);
                } catch (RuntimeException ex) {
                    QT.QUESTS_LOGGER.error("Skipping quest path {}: {}", e.getKey(), ex.getMessage());
                }
            }
            loaded = b.build();
            QT.QUESTS_LOGGER.info("Loaded {} quest path(s): {}", loaded.size(),
                    loaded.stream().map(QuestPath::id).toList());
        }
    }
}

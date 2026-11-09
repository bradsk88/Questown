package ca.bradj.questown.town.quests;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Numbers identical quests in the order they were added, so the second "Bedroom" shows as "Bedroom #2". */
public class QuestOrdinals {

    private record Key(Quest.QuestType type, Object wantedId, int count) {
    }

    /** Each quest's 1-based position among the quests in {@code inOrder} that would show the same name. */
    public static <Q extends Quest<?, ?>> Map<Q, Integer> of(List<? extends Q> inOrder) {
        Map<Key, Integer> seen = new HashMap<>();
        Map<Q, Integer> out = new HashMap<>();
        for (Q q : inOrder) {
            Key k = new Key(q.getType(), q.getWantedId(), q.getCountNeeded());
            out.put(q, seen.merge(k, 1, Integer::sum));
        }
        return out;
    }
}

package ca.bradj.questown.jobs;

import ca.bradj.questown.town.AbstractWorkStatusStore;
import ca.bradj.roomrecipes.core.space.Position;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class JobSitesTest {

    @Test
    public void returnNullIfNoMatches() {
        Position result = JobSites.find(
                ImmutableList::of,
                (match) -> ImmutableList.of(),
                match -> null,
                pos -> AbstractWorkStatusStore.State.fresh(),
                ImmutableMap.of(),
                2,
                ImmutableList.of(),
                new TestPosKit()
        );
        Assertions.assertNull(result);
    }

}
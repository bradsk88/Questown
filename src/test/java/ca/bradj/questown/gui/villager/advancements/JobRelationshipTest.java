package ca.bradj.questown.gui.villager.advancements;

import ca.bradj.questown.jobs.JobID;
import com.google.common.collect.ImmutableList;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class JobRelationshipTest {

    @Test
    public void testLeafNodesCount1() {
        int nodes = new JobRelationship(
                null,
                ImmutableList.of(
                        new JobRelationship(
                                new JobID("1", "1"),
                                ImmutableList.of()
                        ),
                        new JobRelationship(
                                new JobID("1", "1"),
                                ImmutableList.of()
                        ),

                        new JobRelationship(
                                new JobID("1", "1"),
                                ImmutableList.of()
                        ),

                        new JobRelationship(
                                new JobID("1", "1"),
                                ImmutableList.of()
                        )
                )
        ).countLeafNodes();
        Assertions.assertEquals(4, nodes);
    }
    @Test
    public void testLeafNodesCount2() {
        int nodes = new JobRelationship(
                null,
                ImmutableList.of(
                        new JobRelationship(
                                new JobID("1", "1"),
                                ImmutableList.of(
                                        new JobRelationship(
                                                new JobID("1", "1"),
                                                ImmutableList.of()
                                        ),

                                        new JobRelationship(
                                                new JobID("1", "1"),
                                                ImmutableList.of()
                                        )
                                )
                        ),
                        new JobRelationship(
                                new JobID("1", "1"),
                                ImmutableList.of(
                                        new JobRelationship(
                                                new JobID("1", "1"),
                                                ImmutableList.of()
                                        ),

                                        new JobRelationship(
                                                new JobID("1", "1"),
                                                ImmutableList.of()
                                        )
                                )
                        )
                )
        ).countLeafNodes();
        Assertions.assertEquals(4, nodes);
    }
    @Test
    public void testLeafNodesCount3() {
        int nodes = new JobRelationship(
                null,
                ImmutableList.of(
                        new JobRelationship(
                                new JobID("1", "1"),
                                ImmutableList.of(
                                        new JobRelationship(
                                                new JobID("1", "1"),
                                                ImmutableList.of()
                                        ),

                                        new JobRelationship(
                                                new JobID("1", "1"),
                                                ImmutableList.of()
                                        )
                                )
                        ),
                        new JobRelationship(
                                new JobID("1", "1"),
                                ImmutableList.of(
                                        new JobRelationship(
                                                new JobID("1", "1"),
                                                ImmutableList.of()
                                        ),

                                        new JobRelationship(
                                                new JobID("1", "1"),
                                                ImmutableList.of()
                                        )
                                )
                        ),
                        new JobRelationship(
                                new JobID("1", "1"),
                                ImmutableList.of()
                        )
                )
        ).countLeafNodes();
        Assertions.assertEquals(5, nodes);
    }

}
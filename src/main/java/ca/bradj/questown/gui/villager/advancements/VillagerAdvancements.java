package ca.bradj.questown.gui.villager.advancements;

import ca.bradj.questown.jobs.gatherer.*;
import com.google.common.collect.ImmutableList;

public class VillagerAdvancements {

    private static final JobRelationship all = new JobRelationship(
            null,
            ImmutableList.of(
                    new JobRelationship(
                            GathererUnmappedNoToolWork.ID,
                            ImmutableList.of(
                                    new JobRelationship(
                                            GathererUnmappedShovelWork.ID,
                                            ImmutableList.of()
                                    ),
                                    new JobRelationship(
                                            GathererUnmappedAxeWork.ID,
                                            ImmutableList.of(
                                                    new JobRelationship(
                                                            GathererMappedAxeWork.ID,
                                                            ImmutableList.of()
                                                    )
                                            )
                                    ),
                                    new JobRelationship(
                                            GathererUnmappedPickaxeWork.ID,
                                            ImmutableList.of()
                                    )
                            )
                    )
            )
    );

    public static JobRelationship all() {
        return all;
    }
}

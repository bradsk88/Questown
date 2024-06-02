package ca.bradj.questown.jobs.blacksmith;

import ca.bradj.questown.jobs.JobDefinition;
import ca.bradj.questown.jobs.JobID;
import com.google.common.collect.ImmutableMap;

public class BlacksmithWoodenPickaxeNoMCJob {
    public static final int BLOCK_STATE_NEED_HANDLE = 0;
    public static final int BLOCK_STATE_NEED_HEAD = 1;
    public static final int BLOCK_STATE_NEED_WORK = 2;
    public static final int BLOCK_STATE_DONE = 3;

    public static final JobDefinition DEFINITION = new JobDefinition(
            new JobID("blacksmith", "wooden_pickaxe"),
            BLOCK_STATE_DONE,
            ImmutableMap.of(
                    BLOCK_STATE_NEED_HANDLE, "minecraft:stick",
                    BLOCK_STATE_NEED_HEAD, "#minecraft:planks",
                    BLOCK_STATE_NEED_WORK, "#minecraft:planks"
            ),
            ImmutableMap.of(
                    BLOCK_STATE_NEED_HANDLE, 2,
                    BLOCK_STATE_NEED_HEAD, 3,
                    BLOCK_STATE_NEED_WORK, 0
            ),
            ImmutableMap.of(
            ),
            ImmutableMap.of(
                    BLOCK_STATE_NEED_HANDLE, 0,
                    BLOCK_STATE_NEED_HEAD, 0,
                    BLOCK_STATE_NEED_WORK, 10,
                    BLOCK_STATE_DONE, 0
            ),
            ImmutableMap.of(
                    BLOCK_STATE_NEED_HANDLE, 0,
                    BLOCK_STATE_NEED_HEAD, 0,
                    BLOCK_STATE_NEED_WORK, 0,
                    BLOCK_STATE_DONE, 0
            ),
            "minecraft:wooden_pickaxe"
    );
}

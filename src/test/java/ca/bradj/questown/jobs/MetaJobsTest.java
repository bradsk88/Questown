package ca.bradj.questown.jobs;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class MetaJobsTest {

    @Test
    void theHuntersUpkeepJobs_areMeta() {
        for (String id : new String[]{
                "dining", "dining_no_table", "dining_raw_food", "downtime", "resting", "seeking_work",
                "bop_depositing"
        }) {
            Assertions.assertTrue(MetaJobs.isMeta(new JobID("hunter", id)), id);
        }
    }

    @Test
    void theHuntersWork_isNotMeta() {
        Assertions.assertFalse(MetaJobs.isMeta(new JobID("hunter", "sword")));
        Assertions.assertFalse(MetaJobs.isMeta(new JobID("hunter", "sword_half_day")));
    }
}

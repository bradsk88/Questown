package ca.bradj.questown.jobs.declarative;

import ca.bradj.questown.jobs.production.ProductionStatus;

public interface Dependencies3<RECIPE> {
    boolean isFarm(RECIPE x);

    ProductionStatus getComputeStatusOverrideForSpecialJobs();

    ProductionJournal<?,?> getJournal();
}

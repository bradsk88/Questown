package ca.bradj.questown.jobs.integration;

import ca.bradj.questown.jobs.GathererJournalTest;
import ca.bradj.questown.jobs.JobID;
import ca.bradj.questown.jobs.SignalSource;
import ca.bradj.questown.jobs.Signals;
import ca.bradj.questown.jobs.declarative.ProductionJournal;
import ca.bradj.questown.jobs.production.ProductionStatus;

/**
 * Test implementation of ProductionJournal for integration tests.
 */
public class TestProductionJournal extends ProductionJournal<GathererJournalTest.TestItem, GathererJournalTest.TestItem> {

    public TestProductionJournal(JobID jobId, int capacity) {
        super(
                jobId,
                new TestSignalSource(),
                capacity,
                () -> new GathererJournalTest.TestItem(""),
                ProductionStatus.FACTORY
        );
    }

    private static class TestSignalSource implements SignalSource {
        @Override
        public Signals getSignal(Signals.DayTime dayTime) {
            // Return MORNING as a reasonable default for tests (daytime, working hours)
            return Signals.MORNING;
        }
    }
}

package ca.bradj.questown.jobs;

import ca.bradj.questown.jobs.production.ProductionStatus;
import ca.bradj.roomrecipes.core.space.Position;
import org.junit.jupiter.api.Test;

class JobLogicTest {


    private static class TestLogicWorld implements JobLogic.JLWorld<Position> {

        private WorkSpot<Integer, Position> workspot;

        @Override
        public void changeJob(JobID id) {
        }

        @Override
        public WorkSpot<Integer, Position> getWorkSpot() {
            return this.workspot;
        }

        @Override
        public void clearWorkSpot(String reason) {
            this.workspot = null;
        }

        @Override
        public boolean tryGrabbingInsertedSupplies() {
            return false;
        }

        @Override
        public boolean tryWorking() {
            return false;
        }

        @Override
        public boolean canDropLoot() {
            return false;
        }

        @Override
        public void tryDropLoot() {

        }

        @Override
        public void tryGetSupplies() {

        }

        @Override
        public void seekFallbackWork() {

        }
    }

    @Test
    void tick_ShouldShouldWorkIfIngredientsNeededAndHad() {
        new JobLogic().tick(
                () -> ProductionStatus.fromJobBlockStatus(0),
                new JobID("test", "tester"),
                ExpirationRules.never(),
                new TestLogicWorld()
        );
    }
}
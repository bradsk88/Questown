package ca.bradj.questown.jobs;

import com.google.common.collect.ImmutableList;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class ContainersClean {

    public interface Block<CONTAINER> {

        boolean isAir();

        boolean isJobBlock();

        @Nullable CONTAINER asContainer();

        @Nullable CONTAINER asChest();
    }

    public interface JobSite<CONTAINER> {
        ImmutableList<Block<CONTAINER>> getBlocks();

        boolean isJobSite();
    }

    public static <CONTAINER> List<CONTAINER> get(
            Collection<JobSite<CONTAINER>> rooms,
            boolean stopAfterOneFound
    ) {
        List<CONTAINER> chests = new ArrayList<>();
        for (JobSite<CONTAINER> c : rooms) {
            for (Block<CONTAINER> block : c.getBlocks()) {
                if (block.isAir()) {
                    continue;
                }
                boolean containerIsNotInJobSite = !c.isJobSite();
                boolean containerIsNotJobTarget = !block.isJobBlock();
                if (containerIsNotInJobSite || containerIsNotJobTarget) {
                    boolean added = addIfChest(block, chests);
                    if (added && stopAfterOneFound) {
                        return chests;
                    }
                }
                if (containerIsNotJobTarget) {
                    boolean added = addIfContainer(block, chests);
                    if (added && stopAfterOneFound) {
                        return chests;
                    }
                }
            }
        }
        return chests;
    }

    private static <CONTAINER> boolean addIfContainer(
            Block<CONTAINER> block,
            List<CONTAINER> chests
    ) {
        CONTAINER container = block.asContainer();
        if (container == null) {
            return false;
        }
        chests.add(container);
        return true;
    }

    private static <CONTAINER> boolean addIfChest(
            Block<CONTAINER> block,
            List<CONTAINER> chests
    ) {
        CONTAINER container = block.asChest();
        if (container == null) {
            return false;
        }
        chests.add(container);
        return true;
    }
}

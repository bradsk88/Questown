package ca.bradj.questown.jobs;

import ca.bradj.questown.jobs.gatherer.NewLeaverWork;
import ca.bradj.questown.jobs.requests.WorkRequest;

public interface MustMatchTags {
    NewLeaverWork.TagsCriteria getNecessaryTags(WorkRequest requestedResult);
}

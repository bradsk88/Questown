package ca.bradj.questown.town;

import ca.bradj.questown.jobs.JobID;
import ca.bradj.questown.jobs.gatherer.NewLeaverWork;

public record JobApplication(JobID p, NewLeaverWork.TagsCriteria criteria) {
}

package ca.bradj.questown.town;

import ca.bradj.questown.jobs.JobID;
import ca.bradj.questown.jobs.requests.WorkRequest;

public record JobApplication(JobID p, WorkRequest requestedResult) {
}

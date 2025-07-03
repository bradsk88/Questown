package ca.bradj.questown.integration.jobs;

import ca.bradj.questown.jobs.JobBlockTestContext;

import java.util.function.Predicate;

public interface JobCheck extends Predicate<JobBlockTestContext> {

}

package com.aamir.config;

import lombok.extern.slf4j.Slf4j;
import org.jobrunr.jobs.Job;
import org.jobrunr.jobs.filters.ApplyStateFilter;
import org.jobrunr.jobs.filters.JobServerFilter;

import org.jobrunr.jobs.states.FailedState;
import org.jobrunr.jobs.states.JobState;
import org.springframework.stereotype.Component;

/**
 * Observability hook. JobRunr invokes every registered JobServerFilter on each
 * job state transition — the same information the dashboard shows, but available
 * to us programmatically so a permanently failed job can page on-call.
 *
 * A job is permanently failed when its total number of FailedState transitions
 * exceeds its configured amountOfRetries (JobRunr stops retrying at that point
 * and leaves the job in FAILED). No PermanentlyFailedState exists in OSS, hence
 * the manual count.
 */
@Slf4j
@Component
public class JobEventListener implements ApplyStateFilter {

    @Override
    public void onStateApplied(Job job, JobState oldState, JobState newState) {
        if (newState instanceof FailedState failed) {
            long failureCount = job.getJobStatesOfType(FailedState.class).count();
            int maxRetries = job.getAmountOfRetries() == null ? 0 : job.getAmountOfRetries();
            if (failureCount > maxRetries) {
                log.error("Job {} PERMANENTLY failed after {} attempts: {} [{}]",
                        job.getJobSignature(), failureCount, failed.getExceptionType(), failed.getExceptionMessage());
                // In production: call PagerDuty / Slack / an alerting webhook here.
            }
        }
    }
}
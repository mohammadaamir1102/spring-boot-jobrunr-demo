package com.aamir.task;


import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * The ONE legitimate case for plain @Scheduled in a horizontally-scaled service:
 * a task where running it on every node independently is harmless (or even
 * desired), e.g. logging this node's own JVM health / local cache warm status.
 * No ShedLock needed here either — because we WANT every pod to do this.
 *
 * If duplicate execution across nodes would ever be a problem, it belongs in
 * RecurringJobs.java as a JobRunr @Recurring job instead, not here.
 */
@Slf4j
@Component
public class HeartbeatTask {

    @Scheduled(fixedRate = 60_000) //
    public void logNodeHeartbeat() {
        log.debug("Node heartbeat OK - pid={}", ProcessHandle.current().pid());
    }
}

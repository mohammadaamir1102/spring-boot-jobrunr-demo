package com.aamir.service;


import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Service
public class ErpSyncService {

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * Called every 30 minutes by a recurring job to push stock levels to the
     * external ERP. External systems are flaky by nature — this is exactly the
     * scenario JobRunr's automatic retry-with-backoff policy (@Job(retries = ...))
     * is meant for: on failure JobRunr re-throws are caught, the job is marked
     * FAILED, and JobRunr automatically re-schedules it with exponential backoff
     * (10s, 20s, 40s ... configurable) without any extra code from us.
     */
    public void pushStockLevelsToErp() {
        log.info("Pushing stock levels to external ERP...");
        try {
            // simulate an unreliable external call
            restTemplate.getForEntity("https://erp.example.com/api/stock/sync", String.class);
        } catch (RestClientException e) {
            log.error("ERP sync failed, JobRunr will retry with backoff", e);
            throw e; // rethrow -> JobRunr marks job FAILED and retries automatically
        }
        log.info("ERP sync completed successfully");
    }
}
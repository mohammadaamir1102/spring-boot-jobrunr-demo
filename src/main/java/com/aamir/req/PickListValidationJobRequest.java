package com.aamir.req;

import com.aamir.service.PickListService;
import org.jobrunr.jobs.annotations.Job;
import org.jobrunr.jobs.lambdas.JobRequest;
import org.jobrunr.jobs.lambdas.JobRequestHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * CHAINED / continuation job — runs after the pick-list batch for a warehouse.
 */
public record PickListValidationJobRequest(String warehouseCode, int totalOrders) implements JobRequest {

    @Override
    public Class<PickListValidationJobRequestHandler> getJobRequestHandler() {
        return PickListValidationJobRequestHandler.class;
    }

    @Component
    public static class PickListValidationJobRequestHandler
            implements JobRequestHandler<PickListValidationJobRequest> {

        private final PickListService pickListService;

        public PickListValidationJobRequestHandler(PickListService pickListService) {
            this.pickListService = pickListService;
        }

        @Override
        @Job(name = "Validate pick-list batch for %0", retries = 2)
        public void run(PickListValidationJobRequest jobRequest) {
            pickListService.validateAllPickListsGenerated(jobRequest.totalOrders());
        }
    }
}
package com.aamir.req;

import com.aamir.repo.OrderRepository;
import com.aamir.service.PickListService;
import org.jobrunr.jobs.annotations.Job;
import org.jobrunr.jobs.lambdas.JobRequest;
import org.jobrunr.jobs.lambdas.JobRequestHandler;
import org.springframework.stereotype.Component;

/**
 * One child job = one order's pick-list. Fanned out by BulkPickListBatchService.
 */
public record PickListGenerationJobRequest(Long orderId) implements JobRequest {

    @Override
    public Class<PickListGenerationJobRequestHandler> getJobRequestHandler() {
        return PickListGenerationJobRequestHandler.class;
    }

    @Component
    public static class PickListGenerationJobRequestHandler
            implements JobRequestHandler<PickListGenerationJobRequest> {


        private final OrderRepository orderRepository;
        private final PickListService pickListService;

        public PickListGenerationJobRequestHandler(OrderRepository orderRepository, PickListService pickListService) {
            this.orderRepository = orderRepository;
            this.pickListService = pickListService;
        }


        @Override
        @Job(name = "Generate pick-list for order #%0", retries = 3)
        public void run(PickListGenerationJobRequest jobRequest) {
            orderRepository.findById(jobRequest.orderId())
                    .ifPresent(pickListService::generatePickListFor);
        }
    }
}

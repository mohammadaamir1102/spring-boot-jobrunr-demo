package com.aamir.req;


import com.aamir.service.OrderService;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import org.jobrunr.jobs.annotations.Job;
import org.jobrunr.jobs.lambdas.JobRequest;
import org.jobrunr.jobs.lambdas.JobRequestHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * FIRE-AND-FORGET job.
 * <p>
 * We use the JobRequest/JobRequestHandler pattern (instead of raw lambdas like
 * `BackgroundJob.enqueue(() -> service.process(id))`) because:
 * 1. It's refactor-safe — lambdas are serialized by method reference, and can
 * break if you rename methods; JobRequests are plain serializable DTOs.
 * 2. It plays nicely with Spring DI — JobRunr resolves the JobRequestHandler
 * bean from the ApplicationContext instead of trying to recreate a lambda's
 * enclosing instance, which matters a lot in a distributed multi-node setup
 * because the enqueuing node and the executing node are usually different
 * JVMs entirely.
 */
public record OrderProcessingJobRequest(Long orderId) implements JobRequest {

    @Override
    public Class<OrderProcessingJobRequestHandler> getJobRequestHandler() {
        return OrderProcessingJobRequestHandler.class;
    }

    @Component
    public static class OrderProcessingJobRequestHandler implements JobRequestHandler<OrderProcessingJobRequest> {


        private final OrderService orderService;

        public OrderProcessingJobRequestHandler(OrderService orderService) {
            this.orderService = orderService;
        }

        @Override
        @Job(name = "Process new order #%0", retries = 3)
        public void run(OrderProcessingJobRequest jobRequest) {
            orderService.processNewOrder(jobRequest.orderId());
        }
    }
}

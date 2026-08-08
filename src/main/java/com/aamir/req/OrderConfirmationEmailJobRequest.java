package com.aamir.req;


import com.aamir.service.NotificationService;
import org.jobrunr.jobs.annotations.Job;
import org.jobrunr.jobs.lambdas.JobRequest;
import org.jobrunr.jobs.lambdas.JobRequestHandler;
import org.springframework.stereotype.Component;

/**
 * DELAYED job — enqueued with a target time in the future.
 * See OrderController: scheduled 10 minutes after order placement.
 */
public record OrderConfirmationEmailJobRequest(Long orderId, String customerEmail) implements JobRequest {

    @Override
    public Class<OrderConfirmationEmailJobRequestHandler> getJobRequestHandler() {
        return OrderConfirmationEmailJobRequestHandler.class;
    }

    @Component
    public static class OrderConfirmationEmailJobRequestHandler
            implements JobRequestHandler<OrderConfirmationEmailJobRequest> {


        private final NotificationService notificationService;

        public OrderConfirmationEmailJobRequestHandler(NotificationService notificationService) {

            this.notificationService = notificationService;
        }

        @Override
        @Job(name = "Send confirmation email for order #%0", retries = 5)
        public void run(OrderConfirmationEmailJobRequest jobRequest) {
            notificationService.sendOrderConfirmationEmail(jobRequest.orderId(), jobRequest.customerEmail());
        }
    }
}

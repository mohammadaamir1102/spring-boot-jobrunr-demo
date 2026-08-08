package com.aamir.req;


import com.aamir.service.ReportService;
import org.jobrunr.jobs.annotations.Job;
import org.jobrunr.jobs.context.JobContext;
import org.jobrunr.jobs.lambdas.JobRequest;
import org.jobrunr.jobs.lambdas.JobRequestHandler;
import org.springframework.stereotype.Component;

/**
 * One-shot variant of the midday recurring report job.
 * Registered as @Recurring in RecurringJobs.java; this request lets ops trigger
 * the same work on demand (see JobDemoController) and get dashboard progress logs.
 */
public record MiddayDispatchReportJobRequest() implements JobRequest {

    @Override
    public Class<MiddayDispatchReportJobRequestHandler> getJobRequestHandler() {
        return MiddayDispatchReportJobRequestHandler.class;
    }

    @Component
    public static class MiddayDispatchReportJobRequestHandler
            implements JobRequestHandler<MiddayDispatchReportJobRequest> {

        private final ReportService reportService;

        public MiddayDispatchReportJobRequestHandler(ReportService reportService) {
            this.reportService = reportService;
        }

        @Override
        @Job(name = "Midday dispatch report (manual trigger)", retries = 2)
        public void run(MiddayDispatchReportJobRequest jobRequest) {
            reportService.generateMiddayDispatchReport(jobContext());
        }
    }
}
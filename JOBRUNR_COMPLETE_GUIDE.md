# JobRunr: Complete Feature Guide & Dashboard Overview

## Table of Contents
1. [What is JobRunr?](#what-is-jobrunr)
2. [Problems JobRunr Solves](#problems-jobrunr-solves)
3. [Key Features](#key-features)
4. [Why Use JobRunr?](#why-use-jobrunr)
5. [Dashboard Overview](#dashboard-overview)
6. [Dashboard Detailed Features](#dashboard-detailed-features)
7. [Real-World Use Cases](#real-world-use-cases)
8. [Best Practices](#best-practices)

---

## What is JobRunr?

**JobRunr** is a distributed job scheduling and processing framework for Java applications that provides:

- **Persistent job storage** using your existing database (MySQL, PostgreSQL, Oracle, etc.)
- **Distributed execution** across multiple application instances
- **Built-in monitoring dashboard** for real-time visibility
- **Automatic retry logic** with backoff strategies
- **Job scheduling** with cron expressions
- **Progress tracking** for long-running tasks
- **Guaranteed at-least-once execution** semantics

JobRunr simplifies background job processing by handling the complexity of distributed execution while providing a unified view across your entire cluster.

---

## Problems JobRunr Solves

### 1. **Lost Jobs in Distributed Systems**
**Problem:** When running on multiple servers, how do you ensure a job runs exactly once?
- **Solution:** JobRunr uses atomic database claims to ensure a job is claimed by only one node.

### 2. **No Visibility into Background Tasks**
**Problem:** Traditional `@Scheduled` or custom thread pools offer no insight into:
- What jobs are running right now?
- Why did a job fail?
- How many times has it been retried?

**Solution:** JobRunr Dashboard provides a single pane of glass for all background jobs across all instances.

### 3. **Difficult Debugging of Failures**
**Problem:** When a background task fails:
- You don't know why it failed
- Stack traces are scattered across logs
- Retrying requires manual intervention or redeployment

**Solution:** JobRunr captures full exception traces, allows manual requeuing, and shows retry history.

### 4. **Scaling Challenges**
**Problem:** Scaling background processing is hard:
- How do you distribute work across multiple nodes fairly?
- How do you know if one node is overloaded?
- Do you have enough worker threads?

**Solution:** JobRunr's Servers tab shows all active workers, their thread pool sizes, and you can scale horizontally.

### 5. **Complex Scheduling & Time Zone Issues**
**Problem:** Cron jobs with `@Scheduled`:
- Are tied to a single application instance
- Don't work across multiple nodes
- Time zone handling is error-prone

**Solution:** JobRunr Recurring Jobs run once per schedule on any available worker, with proper time zone support.

### 6. **No Audit Trail for Compliance**
**Problem:** For regulated industries (finance, healthcare, logistics):
- When exactly did a job run?
- Did it succeed or fail?
- How many retries were attempted?

**Solution:** JobRunr stores complete state history for every job instance—an immutable audit trail.

### 7. **Transient Failures Require Code Deployment**
**Problem:** A job fails due to temporary issues (SMTP down, API rate-limited):
- You must either wait for a retry
- Or redeploy the application

**Solution:** JobRunr Dashboard's "Requeue" button lets you manually retry failed jobs immediately.

---

## Key Features

### 1. **Job Enqueuing & Execution**
```java
@Inject
private BackgroundJob backgroundJob;

// Fire a job immediately
backgroundJob.enqueue(() -> myService.sendEmail("user@example.com"));

// Schedule for later
backgroundJob.schedule(
    Instant.now().plus(1, HOURS),
    () -> myService.processReport()
);

// Recurring (cron)
@Recurring(id = "hourly-sync", cron = "0 * * * * *")
public void syncWithERP() { ... }
```

### 2. **Automatic Retries with Backoff**
```java
@Job(name = "Process Order", retries = 5)
public void processOrder(String orderId) {
    // If this fails, JobRunr retries up to 5 times with exponential backoff
}
```

### 3. **Job Progress Tracking**
```java
@Inject
private JobContext jobContext;

public void processLargePickList(List<Item> items) {
    var progressBar = jobContext.progressBar(items.size());
    
    for (Item item : items) {
        // ... process item
        progressBar.increaseCount();  // Dashboard shows 45% complete
    }
}
```

### 4. **Job Logging**
```java
public void reconcileInventory(InventoryId id) {
    jobContext.logger().info("Starting reconciliation for " + id);
    // ... 
    jobContext.logger().info("Found 12 discrepancies, fixed 11");
    // Logs appear in dashboard in real-time
}
```

### 5. **Distributed Execution**
- Jobs are claimed atomically by workers
- No duplicate execution across nodes
- Horizontal scaling by adding more nodes

### 6. **Dashboard Monitoring**
- Real-time job counters (Succeeded, Failed, Enqueued, Processing, Scheduled)
- Jobs-per-second throughput
- Full job state history and logs
- Retry information and manual requeue capability

---

## Why Use JobRunr?

| Aspect | Benefit |
|--------|---------|
| **Persistence** | Jobs aren't lost if the application crashes; they resume after restart |
| **Observability** | See every job, its state, failures, and logs in one dashboard |
| **Scalability** | Distribute jobs across multiple instances automatically |
| **Reliability** | Automatic retries, backoff, and circuit-breaker patterns |
| **Compliance** | Full audit trail for regulatory requirements |
| **Ease of Use** | Simple Java lambdas; no separate message broker setup |
| **Database-Backed** | Uses your existing DB; no Redis/RabbitMQ/Kafka needed |
| **Time Zone Support** | Cron expressions with proper time zone handling |
| **Flexible Scheduling** | Immediate, delayed, and recurring job patterns |

---

## Dashboard Overview

### Access the Dashboard
- **URL:** `http://localhost:8000/dashboard`
- **Auth:** Basic Auth (default: `admin` / `admin123`)
- **Cluster-Wide:** All nodes read the same JobRunr tables, so you see a unified view

### The Four Main Tabs

| Tab | Purpose | Key Question |
|-----|---------|---------------|
| **Dashboard** | Overview & health | "Is my job system healthy right now?" |
| **Jobs** | Job instances & details | "What happened to job X?" |
| **Recurring Jobs** | Scheduled cron tasks | "What is scheduled to run?" |
| **Servers** | Worker instances | "Am I distributed?" |

---

## Dashboard Detailed Features

### 1. Dashboard / Overview Tab

**What You See:**
- **Counters:** Succeeded, Failed, Enqueued, Processing, Scheduled
- **Jobs Per Second:** Real-time throughput across the cluster
- **Master Server:** The coordinator node and its thread pool size

**Interpreting the Metrics:**

| Metric | Healthy Signal | Warning Signal |
|--------|----------------|----------------|
| **Succeeded** | Climbing steadily | Stalled or declining |
| **Failed** | Flat or very low | Rapidly climbing |
| **Enqueued** | Drains as jobs process | Growing unbounded |
| **Jobs/sec** | Consistent with load | Zero when expecting work |
| **Processing** | Equal to worker threads | All zeros = workers offline |

**Scenarios & Actions:**

```
Scenario: "Did my deploy work?"
→ Look for: Succeeded count climbs, no Failed spike
→ Action: Check once more after 1 minute for delayed jobs

Scenario: "Jobs are queuing but not processing"
→ Look for: Enqueued grows, jobs-per-second ≈ 0
→ Action: Check jobrunr.background-job-server.enabled=true
→ Fix: Verify JOBRUNR_WORKER_ENABLED environment variable

Scenario: "Is a failure happening right now?"
→ Look for: Failed count climbing on each refresh
→ Action: Go to Jobs tab, filter by FAILED, read stack trace

Scenario: "Did midnight job run last night?"
→ Look for: Succeeded counter ≈ expected value
→ Action: Check Recurring Jobs tab, review last run time

Scenario: "Are jobs flooding the queue?"
→ Look for: Enqueued climbing steeply
→ Action: Monitor jobs-per-second; scale workers if saturating
```

---

### 2. Jobs Tab (The Workhorse)

**Features Available:**

#### A. Search & Filter
```
Search by: Job name, ID, or labels
Filter by: SCHEDULED, ENQUEUED, PROCESSING, SUCCEEDED, FAILED, DELETED
Pagination: Browse through thousands of jobs efficiently
```

#### B. Job State History Timeline
Shows every transition with timestamps and duration:
```
SCHEDULED (10:00:00)
    ↓ (1 second)
ENQUEUED (10:00:01)
    ↓ (5 seconds)
PROCESSING (10:00:06)
    ↓ (failure)
FAILED (10:00:12)
    ↓ (2 minutes, backoff retry)
ENQUEUED (10:02:12)
    ↓ (5 seconds)
PROCESSING (10:02:17)
    ↓ (success)
SUCCEEDED (10:02:20)
```

**Use When:** Debugging "was this job retried?" and "how long did it take?"

#### C. Retry Counter & Details
```
Current Retry: 2 / 5 retries
Next Attempt: In 30 seconds
Backoff Strategy: Exponential (1s → 2s → 4s → 8s → 16s)
```

**Use When:** Determining if a job is dead or will auto-recover

#### D. Failure Stack Trace
Full exception message + complete stack trace captured in the dashboard.

**Use When:** Root-causing a production bug without SSH-ing into pods

#### E. Job Logs (Per-Job Stream)
```
10:00:06 [INFO] InventoryService: Starting reconciliation for INV-001
10:00:07 [INFO] InventoryService: Processing 1,250 SKUs
10:00:09 [INFO] InventoryService: Found 15 discrepancies
10:00:10 [INFO] InventoryService: Fixed 14 automatically
10:01:20 [WARN] InventoryService: SKU-XYZ-123 requires manual review
10:01:22 [INFO] InventoryService: Reconciliation completed in 76 seconds
```

**Use When:** Monitoring long-running jobs in real-time without log aggregation

#### F. Progress Bar
Shows percentage completion for jobs that report progress:
```
Processing Pick List [████████░░░░░░░░░░░░░░░░] 35% (1,750 / 5,000 items)
```

**Use When:** Understanding if a batch job is progressing or stuck

#### G. Job Information & Metadata
- Job class and method name
- Labels (custom tags for organization)
- Created/updated timestamps
- Job arguments and return value

**Use When:** Identifying which job was triggered by which user action

#### H. Job Actions

**Requeue Button:** For FAILED jobs
```
Use Case: SMTP creds were wrong, email failed.
Action:   Fix the SMTP config, click Requeue.
Result:   Job runs immediately without waiting for next retry window.
```

**Delete Button:** Remove job from system
```
Use Case: A bad recurring job keeps filling the queue.
Action:   Delete all instances or just the recurring definition.
Result:   Job no longer shows in dashboard; DB cleanup happens per retention policy.
```

**Scenarios & Solutions:**

```
Scenario: "Customer says order is still PLACED"
→ Action: Filter by "Process new order #4"
→ Check: Did it FAILED? Was it retried? Never enqueued?
→ Next: If missing, look for enqueueing failure in application logs

Scenario: "ERP sync keeps failing"
→ Action: Check FAILED jobs for ErpSyncService
→ Check: Retry counter (usually retries=5 with backoff)
→ Action: Only requeue if you've fixed the root cause

Scenario: "A report job looks like it ran on the wrong pod"
→ Action: JobRunr atomic claims prevent duplicates
→ If you see two runs: Likely a recurring job running at schedule boundary
→ Fix: Verify cron expression and time zone settings

Scenario: "A bad job is retried forever"
→ Action: Check the retry cap via @Job(retries=...)
→ Fix: Lower the cap, or delete the job manually
→ Prevention: Remove from recurring jobs list

Scenario: "Need to clean old data"
→ Action: Delete old runs manually from dashboard
→ Or: Let delete-succeeded-jobs-after retention config auto-cleanup

Scenario: "Need audit trail for compliance"
→ Action: Export state history timeline from job detail
→ Result: Screenshot/screenshot = immutable per-job history
```

---

### 3. Recurring Jobs Tab

**What You See:**
- Every `@Recurring` schedule defined in your code
- Cron expression for each
- Time zone (e.g., Asia/Kolkata)
- Last run status and next scheduled run

**Example Recurring Jobs:**

```
┌─────────────────────────────────────────────────────┐
│ Recurring ID                  │ Cron         │ Zone │
├────────────────────────────────────────────────────┤
│ midnight-inventory-reconciliation │ 0 0 0 * * * │ Asia/Kolkata │
│ midday-dispatch-report        │ 0 0 12 * * * │ Asia/Kolkata │
│ hourly-stock-threshold-check  │ 0 0 * * * *  │ UTC  │
│ erp-stock-sync               │ 0 */30 * * * * │ UTC  │
└─────────────────────────────────────────────────────┘
```

**Features:**

| Feature | Details |
|---------|---------|
| **View Schedule** | See the exact cron + time zone for each recurring job |
| **Last Run Status** | View last execution result: Succeeded, Failed, or Scheduled |
| **Next Run Time** | Countdown to next scheduled execution |
| **Edit Schedule** | Modify cron/zone in code, redeploy; changes take effect next registration |
| **Trigger Now** | OSS has no button, but use HTTP endpoint: `curl -X POST localhost:8080/api/v1/jobs/recurring/{id}/trigger` |
| **Delete Schedule** | Remove a recurring job (optional: also remove `@Recurring` annotation from code) |

**Scenarios & Solutions:**

```
Scenario: "What runs after midnight?"
→ Action: Open Recurring Jobs tab
→ Result: Complete schedule in one page
→ Share: With operations for planning

Scenario: "Schedule moved to different warehouse timezone"
→ Action: Edit zoneId in RecurringJobs.java
→ Redeploy application
→ Effect: New time zone applies next registration

Scenario: "Need to test midnight job now"
→ Action: Get recurring job ID from this tab
→ Execute: curl -X POST localhost:8080/api/v1/jobs/recurring/midnight-inventory-reconciliation/trigger
→ Result: Job runs immediately for testing

Scenario: "Recurring job keeps failing, want it gone"
→ Action: Delete from Recurring Jobs tab
→ Or: Remove @Recurring annotation from code and redeploy
→ Important: Fix root cause first to avoid regression
```

---

### 4. Servers Tab

**What You See:**
- Every `BackgroundJobServer` currently registered and active
- **Hostname** and instance ID
- **Worker pool size** (number of threads available)
- **Poll interval** (how often workers check for jobs)
- **Which node is the master** (the leader coordinating job claiming)
- **Uptime and heartbeat** to detect dead pods

**Example Server List:**

```
┌──────────────────────────────────────────────────────────────────┐
│ Hostname          │ Pool Size │ Poll Interval │ Status │ Master? │
├───────────────────┼───────────┼───────────────┼────────┼─────────┤
│ wms-worker-1      │ 10        │ 5s            │ Active │ YES     │
│ wms-worker-2      │ 10        │ 5s            │ Active │ NO      │
│ wms-worker-3      │ 10        │ 5s            │ Active │ NO      │
└──────────────────────────────────────────────────────────────────┘
```

**Features:**

| Feature | Use Case |
|---------|----------|
| **Server Count** | Verify your cluster size matches expectation |
| **Pool Size** | Know how many parallel jobs per node (tune via `jobrunr.worker-count`) |
| **Master Detection** | See which node is leader; usually the first one online |
| **Heartbeat** | Spot dead pods (missing servers = disconnected nodes) |
| **Auto-Rebalance** | When a node dies, other nodes claim its pending jobs |

**Scenarios & Solutions:**

```
Scenario: "Are my workers actually registered?"
→ Look for: Two lines for wms-worker-1 + wms-worker-2
→ If missing: Check JOBRUNR_WORKER_ENABLED=true + DB connectivity
→ Verify: application.properties has jobrunr.background-job-server.enabled=true

Scenario: "All jobs succeed, but one pod is only executor"
→ Look for: Only one server line; same node always master
→ Explanation: Not a bug—JobRunr executes once; other nodes may be idle
→ This is OK: Jobs are still distributed fairly

Scenario: "A worker node looks unresponsive"
→ Look for: Server line vanishes from Servers tab
→ Explanation: Node was killed or lost DB connectivity
→ Safety: Jobs aren't lost—claiming is atomic; other workers pick them up
→ Action: Investigate node logs; no manual recovery needed
```

---

## Real-World Use Cases

### Use Case 1: Warehouse Management System (WMS)

**Jobs Running:**
- **Order Processing** (enqueued immediately on order submission)
- **Inventory Reconciliation** (midnight, every day, Asia/Kolkata timezone)
- **Dispatch Report** (midday summary for logistics)
- **Stock Threshold Alerts** (hourly, triggers email if stock < threshold)
- **ERP Sync** (every 30 minutes, pushes inventory to external system)

**Dashboard Usage:**
- **Overview:** Check if midnight reconciliation succeeded (look for Succeeded +1)
- **Jobs:** If ERP sync fails, Requeue after fixing the connection
- **Recurring:** Verify all schedules are active and next runs are scheduled
- **Servers:** Ensure 3 worker nodes are registered and healthy

---

### Use Case 2: E-Commerce Platform

**Jobs Running:**
- **Send Order Confirmation Email** (immediate, with retries)
- **Generate PDF Invoice** (delayed, scheduled 1 second after order)
- **Update Search Index** (every product edit, batched every 5 minutes)
- **Send Marketing Campaign** (scheduled for specific date/time, reaches 1M+ users)
- **Clean Abandoned Carts** (daily, removes carts not touched in 7 days)

**Dashboard Usage:**
- **Overview:** Monitor jobs-per-second during campaign launch
- **Jobs:** Search for failed "Send Email" jobs, identify systemic issues (SMTP down?)
- **Recurring:** Verify campaign schedule is correct before launch day
- **Servers:** Scale worker nodes if jobs-per-second drops during peak

---

### Use Case 3: Financial System (PCI-Compliant)

**Jobs Running:**
- **Process Credit Card Charge** (immediate, high retry count)
- **Reconcile Settlements** (nightly, must succeed exactly once)
- **Generate Tax Report** (monthly, immutable audit trail required)
- **Fraud Detection Batch** (every 2 minutes, analyzes recent transactions)

**Dashboard Usage:**
- **Jobs:** Full state history for compliance audits (when did it run? did it succeed?)
- **Retention:** Keep succeeded jobs for 90 days (set `delete-succeeded-jobs-after: P90D`)
- **Alerts:** Wire JobEventListener to PagerDuty when PERMANENTLY_FAILED jobs appear
- **Servers:** High availability—ensure 3+ nodes for failover

---

## Best Practices

### 1. **Job Design**
```java
// ✅ Good: Idempotent, fast, focused
@Job(name = "Send Welcome Email", retries = 3)
public void sendWelcomeEmail(String userId) {
    var user = userRepository.findById(userId);
    emailService.sendTemplate("welcome", user.email());
}

// ❌ Bad: Slow, side effects, no clear name
@Job(retries = 1)
public void doStuff(String param) {
    // ... 10 second database query
    // ... modifies 5 different tables
}
```

### 2. **Retry Strategy**
```java
// ✅ Good: Tune retries per job type
@Job(name = "Sync ERP", retries = 5)  // External API, retryable
public void syncErp() { ... }

@Job(name = "Process Payment", retries = 10)  // Very important, more retries
public void processPayment() { ... }

@Job(name = "Log Analytics Event", retries = 1)  // Telemetry, not critical
public void logEvent() { ... }
```

### 3. **Progress Tracking for Long Jobs**
```java
// ✅ Good: Show progress for 10+ second jobs
@Job(name = "Process Batch", retries = 3)
public void processBatch(List<Item> items) {
    var progressBar = jobContext.progressBar(items.size());
    
    for (Item item : items) {
        processItem(item);
        progressBar.increaseCount();  // Dashboard updates live
    }
}

// ❌ Bad: No visibility into progress
public void processBatch(List<Item> items) {
    for (Item item : items) {
        processItem(item);  // Dashboard shows 0% or 100%, nothing in between
    }
}
```

### 4. **Logging for Debugging**
```java
// ✅ Good: Structured, contextual logs
@Job(name = "Reconcile Inventory", retries = 3)
public void reconcile(InventoryId id) {
    jobContext.logger().info("Starting reconciliation for " + id);
    
    var results = performReconciliation(id);
    jobContext.logger().info("Found " + results.discrepancies() + " discrepancies");
    
    if (results.hasCriticalIssues()) {
        jobContext.logger().warn("Critical issue found: " + results.getIssue());
    }
}

// ❌ Bad: No logs in job context
public void reconcile(InventoryId id) {
    System.out.println("Starting...");  // Not captured in dashboard
    var results = performReconciliation(id);
}
```

### 5. **Recurring Job Scheduling**
```java
// ✅ Good: Named, with time zone, clear purpose
@Recurring(
    id = "midnight-inventory-reconciliation",
    cron = "0 0 0 * * *",  // Midnight
    zoneId = "Asia/Kolkata"
)
public void reconcileInventory() { ... }

// ✅ Good: Frequent batch job
@Recurring(
    id = "erp-stock-sync",
    cron = "0 */30 * * * *",  // Every 30 minutes
    zoneId = "UTC"
)
@Job(retries = 5)
public void syncERP() { ... }

// ❌ Bad: No ID, no time zone (defaults to server time)
@Recurring(cron = "0 0 * * * *")
public void hourlySync() { ... }
```

### 6. **Monitoring & Alerting**
```java
// ✅ Good: Wire alerting to critical failures
@Component
public class JobEventListener implements ApplyStateFilter {
    
    @Override
    public void onStateApplied(Job job, JobState newState, JobState oldState) {
        if (newState instanceof PermanentlyFailedState) {
            slackService.sendAlert(
                "Job FAILED: " + job.getJobName() + 
                " - " + job.getLastJobState().getExceptionMessage()
            );
        }
    }
}

// ❌ Bad: No alerting, hope someone checks dashboard
```

### 7. **Retention & Cleanup**
```yaml
# ✅ Good: Balance visibility with storage
jobrunr:
  background-job-server:
    delete-succeeded-jobs-after: PT48H      # Keep successes 2 days
    permanently-delete-deleted-jobs-after: P7D  # Clean deleted jobs after 7 days
    
# ❌ Bad: Keep everything forever (bloats DB)
jobrunr:
  background-job-server:
    delete-succeeded-jobs-after: P999D  # Never delete
```

### 8. **Scaling Workers**
```properties
# ✅ Good: Match worker pool to workload
jobrunr.worker.thread-count=20  # High-throughput system (many jobs, fast jobs)
jobrunr.worker.thread-count=5   # Low-throughput system (few jobs, slow jobs)

# ✅ Good: Scale horizontally (add more nodes)
# Worker 1: jobrunr.background-job-server.enabled=true, pool=10
# Worker 2: jobrunr.background-job-server.enabled=true, pool=10
# Worker 3: jobrunr.background-job-server.enabled=true, pool=10
# Total: 30 parallel jobs across cluster

# ❌ Bad: One node with huge thread pool
jobrunr.worker.thread-count=1000  # Context switching overhead, single point of failure
```

---

## Troubleshooting Guide

### Problem: Dashboard shows "No servers found"

**Cause:** Workers not registering with database
```
Solution:
1. Verify jobrunr.background-job-server.enabled=true in application.yaml
2. Check JOBRUNR_BACKGROUND_JOB_SERVER_ENABLED=true env var
3. Ensure database connectivity: can app reach MySQL/PostgreSQL?
4. Check logs for "JobRunr" entries; look for connection errors
5. Verify jobrunr tables exist in database (jobrunr_jobs, jobrunr_servers, etc.)
```

### Problem: Jobs are enqueued but never process

**Cause:** No active workers or workers misconfigured
```
Solution:
1. Go to Servers tab—is any node listed?
2. If no: Enable background-job-server on at least one instance
3. If yes: Check jobrunr.worker.thread-count > 0
4. Verify worker poll interval isn't too high: jobrunr.poll-interval-in-seconds=5 (default)
5. Restart the worker and watch Jobs tab for state transitions
```

### Problem: "FAILED" jobs keep failing despite retries

**Cause:** Root cause not fixed, transient failure won't recover
```
Solution:
1. Open the job in Jobs tab
2. Read the stack trace carefully (not just the message)
3. Check the last log entry to see what was happening
4. Fix the actual root cause (database down? API credentials wrong? File not found?)
5. Click Requeue to try immediately with the fix in place
6. Monitor the Recurring Jobs tab to ensure the schedule doesn't keep creating failures
```

### Problem: Cluster has 3 nodes but only 1 processes jobs

**Cause:** Master election or work distribution
```
Solution:
1. This is actually normal; JobRunr can concentrate work on one node
2. To spread load: Increase jobrunr.worker.thread-count on all nodes
3. Increase job enqueue rate: More jobs = more parallel distribution
4. Check if other nodes have workers disabled: Verify jobrunr.background-job-server.enabled=true on all
5. For load testing: Use Apache JMeter to enqueue 1000+ jobs and watch distribution
```

### Problem: Midnight recurring job didn't run

**Cause:** Time zone mismatch or schedule issue
```
Solution:
1. Open Recurring Jobs tab
2. Check zoneId matches your warehouse location
3. Verify cron expression: "0 0 0 * * *" = 00:00:00 (midnight)
4. Use online cron validator: crontab.guru (paste your cron)
5. Look at "Last Run" timestamp—did it run or was it skipped?
6. If now in UTC but should be Asia/Kolkata: Edit code, set zoneId="Asia/Kolkata", redeploy
7. Test immediately: Use Trigger endpoint to verify the job works
```

---

## Integration with Existing Systems

### Logging Integration
```java
// Logs in JobRunr also flow to your SLF4J / ELK / Datadog / Splunk
// Every jobContext.logger().info(...) call writes to both dashboard AND your log aggregation
private static final Logger logger = LoggerFactory.getLogger(MyService.class);

@Job(name = "Process Order")
public void process(String orderId) {
    logger.info("Processing order {}", orderId);  // Goes to ELK
    jobContext.logger().info("Processing order " + orderId);  // Goes to dashboard
}
```

### Alerting Integration
```java
// Wire JobRunr failures to PagerDuty / Slack / OpsGenie
@Component
public class JobFailureAlerter implements ApplyStateFilter {
    
    @Override
    public void onStateApplied(Job job, JobState state, JobState previousState) {
        if (state instanceof PermanentlyFailedState) {
            pagerDutyService.trigger(
                "JobRunr: " + job.getJobName() + " failed",
                job.getJobStates().get(job.getJobStates().size() - 1).getExceptionMessage()
            );
        }
    }
}
```

### Metrics Export
```properties
# Export Micrometer metrics to Prometheus / Grafana
management.endpoints.web.exposure.include=prometheus
# Then scrape:
# curl http://localhost:8080/actuator/prometheus | grep jobrunr
# Dashboard shows: jobs_total, jobs_failed_total, jobs_succeeded_total, job_duration_seconds
```

---

## Security

### Dashboard Authentication
```yaml
# Change default credentials in production
spring:
  security:
    user:
      name: admin
      password: super-secret-password-here

# Enable HTTPS only
server:
  port: 443
  ssl:
    key-store: classpath:keystore.p12
    key-store-password: ${SSL_PASSWORD}
    protocol: TLSv1.2
```

### Database Security
```yaml
# Use encrypted connection to database
spring:
  datasource:
    url: jdbc:mysql://db-host:3306/jobrunr?useSSL=true&requireSSL=true
    username: ${DB_USER}
    password: ${DB_PASSWORD}

# Minimal permissions for JobRunr user
# GRANT SELECT, INSERT, UPDATE, DELETE ON jobrunr.* TO 'jobrunr_user'@'%';
```

### API Authentication
```java
// Dashboard REST API also requires Basic Auth
// GET /api/servers
// Authorization: Basic YWRtaW46YWRtaW4xMjM=  (admin:admin123)

// Automate monitoring:
curl -u admin:admin123 http://localhost:8000/api/dashboard
curl -u admin:admin123 http://localhost:8000/api/jobs?state=FAILED
curl -u admin:admin123 http://localhost:8000/api/servers
```

---

## Comparison with Alternatives

| Feature | JobRunr | Quartz | Scheduled @Scheduled | Spring Batch |
|---------|---------|--------|----------------------|--------------|
| **Distributed** | ✅ Yes | ✅ Yes (with DB) | ❌ No | ✅ Yes |
| **Dashboard** | ✅ Rich UI | ❌ No | ❌ No | ⚠️ Admin console |
| **Database-Backed** | ✅ Yes | ✅ Yes | ❌ In-memory | ✅ Yes |
| **Job Progress** | ✅ Built-in | ❌ Manual | ❌ No | ⚠️ Partial |
| **Retry Strategy** | ✅ Configurable | ✅ Yes | ⚠️ Limited | ✅ Yes |
| **Ease of Use** | ✅ Simple lambdas | ⚠️ Complex XML | ✅ Simple annotation | ⚠️ Verbose |
| **Cloud-Ready** | ✅ Yes | ✅ Yes | ⚠️ Single-node | ✅ Yes |
| **Price** | ✅ OSS + Pro | ✅ OSS | ✅ Free (Spring) | ✅ Free (Spring) |

---

## Conclusion

**JobRunr solves the operational complexity of background job processing.** Instead of building your own retry logic, distribution, and monitoring, JobRunr gives you:

1. **Reliability:** Atomic job claiming, automatic retries, guaranteed execution
2. **Visibility:** Real-time dashboard across the entire cluster
3. **Scalability:** Horizontal distribution with load balancing
4. **Compliance:** Full audit trail for every job
5. **Ease of Use:** Simple Java lambdas, no configuration complexity

The dashboard is not just a monitoring tool—it's your operational command center for background processing. Bookmark it, share it with your team, and use it to diagnose issues within seconds rather than hours of log hunting.

**Start small:** Add a single recurring job and watch it in the dashboard. Then scale to dozens of job types as your confidence grows.

---

## Additional Resources

- **Linked Post:** jobrunr-dashboard-playbook.md (feature-by-feature guide)
- **flow.md:** End-to-end run for new developers
- **README.md:** OSS vs Pro trade-offs and architecture
- **Official Docs:** https://www.jobrunr.io/en/documentation/
- **GitHub:** https://github.com/jobrunr/jobrunr

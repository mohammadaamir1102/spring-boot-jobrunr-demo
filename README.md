# WMS + JobRunr: End-to-End Distributed Background Jobs (Spring Boot 4)

A complete, production-shaped reference implementation showing **every core JobRunr use case**
inside a realistic Warehouse Management System (WMS), running across **multiple distributed
Spring Boot instances**.

> Built to answer one question: *"In a distributed Spring Boot system, do I still need
> `@Scheduled` + ShedLock, or does JobRunr replace all of it?"* — short answer inside.

---

## 1. Why JobRunr for a distributed WMS

A WMS has exactly the kind of background work that breaks naive `@Scheduled` cron jobs the
moment you scale horizontally:

| Requirement | Naive approach | Problem at scale |
|---|---|---|
| Run inventory reconciliation once, every night | `@Scheduled(cron=...)` | Fires on **every pod** → duplicate corrections |
| Prevent duplicate execution across pods | `@Scheduled` + **ShedLock** | Extra library, extra lock table, extra ops burden |
| Retry a flaky ERP call | Manual `try/catch` + custom retry loop | Reinventing backoff, no visibility |
| See what ran, what failed, and why | Log files, grep, hope | No central view across N pods |
| Process 10,000 orders' pick-lists in parallel | Custom thread pool / queue | You're building your own job system |

JobRunr solves all five with **one dependency and zero extra infrastructure**, because job
storage (a table in the same MySQL/Postgres you already have, or Redis) doubles as the
distributed lock.

---

## 2. JobRunr use cases implemented in this repo

| # | Use case | Where | JobRunr mechanism |
|---|---|---|---|
| 1 | **Fire-and-forget** | Order placed → `OrderProcessingJobRequest` | `jobRequestScheduler.enqueue(...)` |
| 2 | **Delayed job** | Confirmation email 10 min after order | `jobRequestScheduler.schedule(OffsetDateTime, ...)` |
| 3 | **Recurring — midnight** | Inventory reconciliation, `0 0 0 * * *` | `@Recurring` |
| 4 | **Recurring — midday** | Dispatch report, `0 0 12 * * *` | `@Recurring` |
| 5 | **Recurring — hourly** | Stock threshold alerting | `@Recurring` |
| 6 | **Recurring — every 30 min** | ERP stock sync | `@Recurring` |
| 7 | **Automatic retries with backoff** | ERP sync (flaky external call) | `@Job(retries = 5)` |
| 8 | **Batch / fan-out** | Bulk pick-list generation, one job per order | N × `jobRequestScheduler.enqueue(...)` |
| 9 | **Job chaining / continuation** | Pick-list validation after the batch | Delayed follow-up job (see note below) |
| 10 | **Dashboard progress + logging** | Midnight reconciliation streams progress | `JobContext.logger()` |
| 11 | **Custom job filters** | Alert when a job permanently fails | `JobServerFilter`, `ApplyStateFilter` |
| 12 | **Manual trigger of a recurring job** | `POST /api/v1/jobs/recurring/{id}/trigger` | Enqueue-one-shot (OSS pattern; `BackgroundJob.triggerRecurringJob` is Pro) |
| 13 | **Distributed dashboard security** | Basic-auth protected dashboard | `org.jobrunr.dashboard.username/password` |
| 14 | **API-only vs Worker-only nodes** | `docker-compose.yml` | `background-job-server.enabled=false` |

> **Honesty note on #9:** true atomic batches and event-driven `.continueWith(...)` chaining are
> **JobRunr Pro** features. This repo shows the open-source-compatible pattern (fan-out N jobs +
> a best-effort delayed continuation) and documents exactly where Pro would upgrade it — see
> `BulkPickListBatchService`.

---

## 3. Project structure

```
com.wms.jobrunr
├── JobRunrWmsApplication.java
├── config/
│   ├── JobEventListenerConfig.java   # job filters (observability, alerting)
│   └── DataSeeder.java               # demo data, profile=demo only
├── domain/                            # Order, OrderStatus, InventoryItem
├── repository/                        # Spring Data JPA
├── service/                           # plain business logic, NO JobRunr imports
│   ├── OrderService, InventoryService, ReportService
│   ├── PickListService, ErpSyncService, NotificationService
├── jobs/                              # everything JobRunr-aware lives here
│   ├── RecurringJobs.java             # midnight / midday / hourly / 30-min
│   ├── OrderProcessingJobRequest.java # fire-and-forget
│   ├── OrderConfirmationEmailJobRequest.java  # delayed
│   ├── PickListGenerationJobRequest.java      # batch child job
│   ├── BulkPickListBatchService.java  # fan-out orchestrator
│   ├── PickListValidationJobRequest.java      # continuation job
│   └── HeartbeatTask.java             # the one OK use of @Scheduled
└── controller/                        # OrderController, JobDemoController
```

**Design rule followed throughout:** service classes contain plain business logic with no
JobRunr imports; the `jobs/` package is the only place that knows about `JobRequest` /
`JobRequestHandler`. This keeps business logic unit-testable without touching JobRunr at all,
and keeps a clean seam if you ever swap job engines.

> **Property-prefix gotcha (this repo hits it):** the Spring Boot starter binds JobRunr
> configuration under the **`jobrunr.*`** prefix (e.g. `jobrunr.background-job-server.enabled`),
> *not* `org.jobrunr.*` as in standalone / older starters. Using the wrong prefix silently
> leaves the worker and dashboard **disabled** (their defaults are `false`) and jobs pile up
> as `ENQUEUED` forever — a classic silent failure. See `application.yaml`.

---

## 4. `@Scheduled` vs `ShedLock` vs JobRunr — the actual decision

```
Does this task's outcome change if it runs on more than one node at once?
│
├── NO (harmless duplicate work, e.g. local cache warmup, JVM heartbeat)
│     → plain @Scheduled is fine. No lock needed. (see HeartbeatTask.java)
│
└── YES (mutates shared state, calls an external system, sends a notification)
      │
      ├── Not using JobRunr already?
      │     → @Scheduled + ShedLock (adds a lock table you must maintain)
      │
      └── Already using JobRunr for other background work?
            → @Recurring (see RecurringJobs.java)
              - No ShedLock, no extra table, no extra library
              - Automatic retries with backoff, built in
              - Full execution history + dashboard visibility, built in
              - One node claims each occurrence via the job storage's own
                atomic write — this IS the distributed lock
```

**Bottom line for this project:** since JobRunr is already the job engine, ShedLock is not
needed anywhere — every job that must not run twice is a JobRunr `@Recurring`/`JobRequest`, and
the one job that's fine running everywhere (`HeartbeatTask`) uses plain `@Scheduled`.

---

## 5. The JobRunr Dashboard — what you actually get

Runs by default at **`http://localhost:8000`** (Basic Auth: `admin` / `admin123`, see
`application.yml`). Every node in the cluster that has `background-job-server.enabled=true`
shares the SAME dashboard view because they all point at the same job storage.

| Dashboard section | What it shows | Why it matters operationally |
|---|---|---|
| **Overview** | Succeeded / Failed / Enqueued / Processing / Scheduled counts, jobs-per-second graph | Instant health check without grepping logs across N pods |
| **Jobs** | Every job instance, filterable by state, with full stack trace on failure | Root-cause a failure in seconds, not by SSH-ing into a pod |
| **Recurring Jobs** | Every `@Recurring` definition, next run time, last run result, and a manual **"Trigger now"** button | Ops can re-run tonight's reconciliation on demand without a deploy |
| **Servers** | Every `BackgroundJobServer` instance (i.e. every worker pod) currently online, its worker pool size, poll interval | Confirms your cluster is actually distributing load — you'll literally see `wms-worker-1` and `wms-worker-2` here |
| **Job detail → Progress bar / Logs** | Live progress + `jobContext.logger()` output streamed in real time | Watch the midnight reconciliation job process SKU-by-SKU without touching a terminal |
| **Job detail → State history** | Every state transition (`SCHEDULED → ENQUEUED → PROCESSING → FAILED → SCHEDULED → ... → SUCCEEDED`) with timestamps and duration | Full audit trail of every retry, "for free" |

Nothing here needs a separate monitoring stack for background-job visibility — it's the single
pane of glass for every job across every instance.

---

## 6. Running it

### Single instance (dev)
```bash
docker compose up mysql -d
./mvnw spring-boot:run -Dspring-boot.run.profiles=demo
# App:       http://localhost:8080
# Dashboard: http://localhost:8000  (admin / admin123)
```

### Full distributed demo (2 workers + 1 API-only node)
```bash
docker compose up --build
# worker 1:  http://localhost:8081  | dashboard http://localhost:8001
# worker 2:  http://localhost:8082  | dashboard http://localhost:8002
# api-only:  http://localhost:8083  (no dashboard, no local worker)
```
Open **either** dashboard (8001 or 8002) → **Servers** tab → you'll see both worker nodes
registered. Trigger the midnight job manually from either dashboard's Recurring Jobs tab and
watch the **Job detail** screen show which single server actually executed it.

### Try it
```bash
# Place an order (fire-and-forget + delayed job enqueued)
curl -X POST localhost:8081/api/v1/orders \
  -H "Content-Type: application/json" \
  -d '{"customerName":"Aamir","customerEmail":"aamir@example.com","sku":"SKU-1001","quantity":2,"warehouseCode":"WH-BLR-01"}'

# Fan out pick-list batch for a warehouse
curl -X POST localhost:8081/api/v1/orders/warehouses/WH-BLR-01/generate-pick-lists

# Manually trigger the midnight recurring job right now
curl -X POST localhost:8081/api/v1/jobs/recurring/midnight-inventory-reconciliation/trigger
```

---

## 7. Production checklist covered here

- [x] Recurring jobs safe across N replicas (no ShedLock needed)
- [x] Automatic retries with exponential backoff on flaky external calls
- [x] Job progress + structured logs visible per-job in a central dashboard
- [x] Dashboard protected with Basic Auth (swap for OIDC/SSO on JobRunr Pro if needed)
- [x] Separate API-only vs Worker-only node profiles for independent scaling
- [x] Custom failure-alerting filter hook for paging on-call
- [x] Business logic (`service/`) fully decoupled from JobRunr (`jobs/`) for testability
- [x] Explicit connection pool sizing (HikariCP) tuned for a worker-heavy workload

---

## About this project

Written as a hands-on reference for teams evaluating JobRunr as a replacement for
`@Scheduled` + ShedLock in a distributed Spring Boot 4 microservices setup, using a
warehouse management system as the running example.
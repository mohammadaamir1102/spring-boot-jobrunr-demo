# flow.md — End‑to‑end walkthrough for a new developer

This file follows ONE order from the moment a developer clicks "Postman/Rest Client"
through to every place JobRunr runs it in the background. Read it top to bottom once,
then use the **Quickstart (5 minutes)** block as your daily driver.

---

## 0. What you are running

A Warehouse Management System (WMS) demo on Spring Boot 4 + JobRunr. The "job system"
gives you, out of the box: fire-and-forget work, delayed work, recurring crons, automatic
retries, and parallelism across a cluster — with a web dashboard that shows everything.

Prerequisites: JDK **21+** (repo tested on 25), Docker (for MySQL), Maven wrapper (bundled).

---

## 1. Reset the environment

```bash
# start a clean MySQL (only needed once; reuse the `mysql` service otherwise)
docker compose up mysql -d

# build + boot the app with demo data seeding
./mvnw spring-boot:run -Dspring-boot.run.profiles=demo

# app:        http://localhost:8080
# JobRunr UI: http://localhost:8000/dashboard   (admin / admin123)
# health:     http://localhost:8080/actuator/health
```

Wait for this log line — it tells you the stack is healthy:

```
Started SpringBootJobrunrDemoApplication
JobRunr BackgroundJobServer (...) using MySqlStorageProvider and 10 BackgroundJobPerformers started successfully
```

---

## 2. What happens while the app starts (read this once)

| Step | Who | Where in the code | Result |
|---|---|---|---|
| Schema created | Hibernate | `entity/Order`, `entity/InventoryItem` | `orders`, `inventory_items` tables |
| JobRunr schema created | JobRunr liquid+SQL migrations | auto at startup | `jobrunr_jobs`, `jobrunr_recurring_jobs`, `jobrunr_metadata`, … |
| Demo data inserted | `DataSeeder` | `config/DataSeeder.java` (only with `demo` profile) | 3 SKUs + 3 orders |
| 4 recurring jobs registered | `RecurringJobs` | `job/RecurringJobs.java` | midnight / midday / hourly / 30-min |
| Worker pool started | JobRunr `BackgroundJobServer` | auto-config (`jobrunr.background-job-server.enabled`) | 10 virtual threads polling the DB every ~15s |
| Dashboard started | `JobRunrDashboardWebServer` | auto-config (`jobrunr.dashboard.enabled`) | UI on :8000 |

> **Gotcha #1 — the property prefix.** The Spring Boot starter binds its config under
> **`jobrunr.*`** (`application.yaml`), NOT `org.jobrunr.*` as in standalone JobRunr. If you
> ever use the wrong prefix, `background-job-server.enabled` and `dashboard.enabled` silently
> stay at their defaults (`false`) → worker is off, jobs pile up in `ENQUEUED`, no dashboard.
> No error is thrown, only a confusing "nothing processes".

> **Where jobs & locks live.** JobRunr stores every job in the **same MySQL you already have**
> (`jobrunr_jobs` etc.), and the atomic DB write that claims a due job IS the distributed lock.
> No ShedLock, no Redis, no extra table.

---

## 3. The deploy that matters: 3 nodes, one DB (scale test)

Run the multi-node compose to see why the "scheduler problem" gets solved:

```bash
docker compose up --build
# worker-1   :8081  dashboard :8001
# worker-2   :8082  dashboard :8002
# api-only   :8083  (no worker, no dashboard — an API/storefront that only enqueues)
```

Now open **both** dashboards → **Servers** tab. You should see `wms-worker-1` and
`wms-worker-2`. Trigger the midnight job from either dashboard and look at the **Job detail**
page: exactly **one** server executes it while the other just waits. That is the distributed
schedule-safety `@Scheduled`+ShedLock was giving you — without ShedLock.

---

## 4. The full order lifecycle — fire-and-forget

| # | Endpoint | In the code | What the background job does |
|---|---|---|---|
| 1. | `POST /api/v1/orders` | `OrderController.placeOrder` | saves the Order, then returns `202` instantly |
| 2. | | `jobRequestScheduler.enqueue(new OrderProcessingJobRequest(id))` | a job is written as `ENQUEUED` |
| 3. | | `jobRequestScheduler.schedule(~now+10min, new OrderConfirmationEmailJobRequest(...))` | second job written as `SCHEDULED` |
| 4. | | any free worker thread | pulls `OrderProcessingJob`, runs `OrderService.processNewOrder` |
| 5. | | Worker calls `order.setStatus(PICK_LIST_GENERATED)` | order moves from `PLACED` to `PICK_LIST_GENERATED` |

```bash
curl -s -X POST localhost:8080/api/v1/orders \
  -H "Content-Type: application/json" \
  -d '{"customerName":"Aamir","customerEmail":"aamir@example.com","sku":"SKU-1001","quantity":2,"warehouseCode":"WH-BLR-01"}'
# → {"orderId":4,"processingJobId":{...},"emailJobId":{...}}   (HTTP 202)
```

Open the dashboard and filter by "Process new order #4" → watch its state history:
`ENQUEUED → PROCESSING → SUCCEEDED`.

---

## 5. Parallel / batch — fan-out

```bash
curl -s -X POST localhost:8080/api/v1/orders/warehouses/WH-BLR-01/generate-pick-lists -o /dev/null -w "%{http_code}\n"   # 202
```

`BulkPickListBatchService` fans it out: **one** `PickListGenerationJobRequest` per PLACED
order → 1 pick-list job. Each job is picked up by whichever worker thread is free → a
10,000‑order batch finishes **in parallel** instead of serially on the pod that received the
request. Then a 2‑minute-delayed `PickListValidationJob` runs to confirm the batch.

_(True atomic batches + strict `.continueWith()` chaining are JobRunr **Pro**; this repo ships
the OSS‑compatible fan‑out pattern.)_

---

## 6. Recurring (cron) work + on‑demand trigger

The 4 crons in `RecurringJobs.java` hit the DB at their wall‑clock time, claimed by exactly
one worker:

- `0 0 0 * * *`  → midnight inventory reconciliation (`Asia/Kolkata`)
- `0 0 12 * * *` → midday dispatch report
- `0 0 * * * *`  → hourly low‑stock check
- `0 */30 * * * *` → ERP stock sync (retries=5, flaky external call)

Test one right now without waiting for midnight:

```bash
curl -s -X POST localhost:8080/api/v1/jobs/recurring/midnight-inventory-reconciliation/trigger -o /dev/null -w "%{http_code}\n"   # 202
curl -s -X POST localhost:8080/api/v1/jobs/recurring/erp-stock-sync/trigger         -o /dev/null -w "%{http_code}\n"
curl -s -X POST localhost:8080/api/v1/jobs/recurring/bogus/trigger                 -o /dev/null -w "%{http_code}\n"   # 400
```

Watch the midnight job's **Job detail** page: it streams `jobContext.logger()` progress
("Starting reconciliation for 3 SKUs … drift found SKU-1001…") — that's per‑job structured
logs off every pod, in one pane of glass.

### Retries in action

`ErpSyncService` calls `https://erp.example.com/...` which always fails in the demo. JobRunr
catches it, marks `FAILED`, and **re‑schedules automatically** with exponential backoff
(10s, 20s, 40s…). Check the table in MySQL to see it:

```bash
docker exec mysql-server mysql -uroot -proot -D jobrunr -N -e \
  "SELECT state, COUNT(*) FROM jobrunr_jobs GROUP BY state;"
# SCHEDULED … (these are jobs waiting for a retry / their delayed time)
# SUCCEEDED …  (the rest)
```

Each retry shows up as another row in the job's **State history** column — an audit trail
you "get for free". That's what the ShedLock route can never give you.

---

## 7. Minimal code map (what to open first)

| File | Purpose |
|---|---|
| `application.yaml` | `jobrunr.*` worker/dashboard switches, DB |
| `job/RecurringJobs.java` | the 4 scheduled cron jobs — THE place a "recurring task" goes in this repo |
| `req/OrderProcessingJobRequest.java` | the fire-and-forget `JobRequest` + its handler |
| `service/OrderService.java` | business logic — **no JobRunr imports**, stays unit‑testable |
| `controller/OrderController.java` & `JobDemoController.java` | HTTP entry points that enqueue |
| `config/JobEventListener.java` | `ApplyStateFilter` — the hook to page on permanent failure |
| `config/DataSeeder.java` | demo seed data (`demo` profile only) |
| `task/HeartbeatTask.java` | the ONE legit `@Scheduled` (runs on every node, harmlessly) |

**Design rule:** `service/*` = pure business. `req/*` = JobRunr glue. JobRunr knows its beans
from the Spring context, so the HTTP thread and the worker can live on different JVMs.

---

## 8. Troubleshooting (the things that usually bite)

### Symptom: jobs stuck `ENQUEUED`
→ Worker disabled. Check `jobrunr.background-job-server.enabled` is `true` (or env
`JOBRUNR_WORKER_ENABLED=true`) **and** spelled `jobrunr.*`, not `org.jobrunr.*`.
Confirm with the log line in §2 and the "Servers" tab in the dashboard.

### Symptom: Lombok "cannot find symbol" at compile time
`annotationProcessorPaths` in `pom.xml` (JDK 21+ no longer auto-discovers processors).
If you remove that block, the build breaks exactly like that.

### Symptom: emails "fail" on the demo
`sendOrderConfirmationEmail` has no SMTP credentials → `JavaMailSender` throws → JobRunr
retries. That's expected. It demonstrates retry, not an outage.

### Symptom: recurring job didn't fire at 00:00
Timezone! The cron runs in `zoneId = "Asia/Kolkata"`. The "trigger" endpoint tests the same
logic on demand.

---

## 9. Your 5‑min daily loop

```bash
docker compose up mysql -d
./mvnw spring-boot:run -Dspring-boot.run.profiles=demo            # terminal 1
curl -s -X POST localhost:8080/api/v1/orders -H "Content-Type: application/json" \
  -d '{"customerName":"Aamir","customerEmail":"a@x.com","sku":"SKU-1001","quantity":2,"warehouseCode":"WH-BLR-01"}'
curl -s -X POST localhost:8080/api/v1/orders/warehouses/WH-BLR-01/generate-pick-lists -o /dev/null -w "\n"
curl -s -X POST localhost:8080/api/v1/jobs/recurring/midnight-inventory-reconciliation/trigger -o /dev/null -w "\n"
open http://localhost:8000/dashboard
```

Open **Dashboard → Jobs** (or **Recurring Jobs**) and watch the per‑job log stream and state
history. You now see the entire WMS running in one pane.

The OSS dashboard has no "Trigger now" button for recurring jobs (that's a Pro feature) — use
`POST /api/v1/jobs/recurring/{id}/trigger` from §6 instead.

---

## TL;DR for your judgment call

- Anything that must **not** run twice across nodes → a JobRunr `@Recurring` or `JobRequest`
  (storage IS the lock). ShedLock not needed.
- The only remaining use for `@Scheduled` is `HeartbeatTask` — work that should run on every
  node.
- Using JobRunr instead of `@Scheduled`+ShedLock this demo gives you: parallel fan‑out,
  automatic backoff retries, per‑job dashboard visibility, delayed jobs, and run‑once‑cluster
  semantics — all with zero extra infra beyond the MySQL you already have.
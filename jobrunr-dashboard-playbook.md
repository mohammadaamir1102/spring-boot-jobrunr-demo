# jobrunr-dashboard-playbook.md — the JobRunr dashboard, feature by feature

The JobRunr dashboard is the single pane of glass for **every background job across every
instance** of this WMS. Boot the app (`./mvnw spring-boot:run -Dspring-boot.run.profiles=demo`),
then open **http://localhost:8000/dashboard** (Basic Auth `admin` / `admin123`).

Every node with `jobrunr.background-job-server.enabled=true` (or with
`jobrunr.dashboard.enabled=true`) renders the SAME dashboard because they all read the same
MySQL `jobrunr_*` tables. So whether you open worker-1 or worker-2, you're looking at the whole
cluster.

> Items marked **(Pro)** need the JobRunr license. Everything else is OSS and in this repo.

---

## 0. The four tabs, at a glance

| Tab | URL | Shows | The question it answers |
|---|---|---|---|
| **Dashboard / Overview** | `…/dashboard` | Global counters + jobs-per-second | "Is my job system healthy right now?" |
| **Jobs** | `…/dashboard/jobs` | Every job instance, filterable | "What exactly happened to job X?" |
| **Recurring Jobs** | `…/dashboard/jobs/recurring` | Every cron schedule + last run | "What is scheduled to run, and when?" |
| **Servers** | `…/dashboard/servers` | Every worker instance online | "Am I actually distributed?" |

---

## 1. Dashboard / Overview tab

### What you can do there

- See live counters: **Succeeded, Failed, Enqueued, Processing, Scheduled**.
- See **jobs per second** processed by the cluster right now.
- See your **master server** (the leader that coordinates job claiming) with its thread pool.

### Scenarios and what to do ("solutions")

| Scenario | Signal you look for | Solution / action |
|---|---|---|
| "Did my deploy just work?" | Succeeded climbing, no red Failed spike | Nothing – healthy. Check once more after a minute for delayed jobs to fire. |
| **Jobs are queuing up but not processing** | Enqueued grows, jobs-per-second ~ 0 | Worker is off. Fix `jobrunr.background-job-server.enabled` / `JOBRUNR_WORKER_ENABLED=true` (and spell it `jobrunr.*`, not `org.jobrunr.*`). See `application.yaml`. |
| "Is a failure underway right now?" | Failed count climbing every refresh | Head to the **Jobs** tab, filter `FAILED`, open the newest job to read its stack trace. |
| "Did the midnight job actually run last night?" | Succeeded vs the expected +1 | Open the job's state history (§2). Retrigger via the trigger endpoint in `flow.md` §6. |
| "Is a flood of jobs about to overwhelm us?" | Enqueued count climbing steeply (e.g. a huge pick-list batch) | Watch jobs-per-second and the enqueued curve; if workers are saturating, scale `worker-count` per node and/or add more worker nodes (compose). |

---

## 2. Jobs tab (the workhorse)

Open **Jobs** and you get every job instance ever created (Scheduled / Enqueued / Processing /
Succeeded / Failed / Deleted) with **search by name**, **filter by state**, and **pagination**.

### Job detail — what you can inspect and DO per job

| Feature | What it shows | When you'd need it |
|---|---|---|
| **State history timeline** | Every transition with timestamps + duration: `SCHEDULED → ENQUEUED → PROCESSING → FAILED → … → SUCCEEDED` | Answering "was there a retry, and how long did each attempt take?" |
| **Retry counter** | `current retry / max retries` visible on the job | "Is this job one of its 5 allowed retries, or is it dead?" |
| **Failure stack trace** | Full exception message + stack for FAILED jobs | Root-cause a production bug without SSH-ing into individual pods |
| **Job logs (per-job stream)** | Everything written via `jobContext.logger().info(...)` streamed live (e.g. `InventoryService.reconcileInventory`) | Watching the midnight reconciliation move SKU-by-SKU in real time during a batch |
| **Progress bars** | `jobContext.progressBar(...)` + percentage | "We're 73% through the pick-list batch" without polling |
| **Job information / labels / metadata** | The job's class, method, labels, created/updated times | Identifying "which job was triggered by which user action" |
| **Requeue (retry now)** | Available on FAILED jobs | You were out of SMTP creds and the confirmation email failed — fix the config, click Requeue, and it runs again on your command |

### Scenarios and "solutions"

| Scenario | What you did / observe | Solution |
|---|---|---|
| **Customer says "my order is still PLACED"** | Filter by "Process new order #4" | See if it FAILED (read stack trace), was retried, or never enqueued. If missing entirely, look for a failure to enqueue in `OrderController`. |
| **ERP sync keeps failing** | The flaky `ErpSyncService` job shows FAILED | That's the feature: check its *retry counter* and *next scheduled* time — backoff is working. Only when `retries` is exhausted does the permanently-failed state appear and your `JobEventListener` logs the alert. |
| **A report job looks like it ran on the "wrong" pod** | Two Succeeded entries for one recurring run | With JobRunr this shouldn't happen (one occurrence is claimed atomically by one node). If you see duplicates, look for a duplicate `@Recurring` definition or a manual trigger plus the cron both firing at once. |
| **A bad job is retried 1,000× forever** | Loop of SCHEDULED → FAILED | Lower the `@Job(retries=...)` cap, or delete the job (see Delete below). |
| **Clean old data** | Old runs no longer relevant | **Delete** via the job detail button (or via the `delete-succeeded-jobs-after` retention config). |
| **Need an audit trail for compliance** | "When exactly did it run, how many retries, what was the error?" | Export the state-history timeline — your immutable per-job history. Screenshot or scrape `GET /api/jobs/{id}`. |

**Tips:**
- The **`Retry` / `Requeue`** button is your "self-heal" loop for genuinely transient failures — nicer than redeploying.
- **Delete** a bad recurring batch by deleting its scheduled instances from this tab (and consider deleting the `@Recurring` definition).

---

## 3. Recurring Jobs tab

Lists every `@Recurring` schedule defined in `RecurringJobs.java`.

| Recurring ID | Cron | In this repo it |
|---|---|---|
| `midnight-inventory-reconciliation` | `0 0 0 * * *` (Asia/Kolkata) | reconciles quantities, fixes drift |
| `midday-dispatch-report` | `0 0 12 * * *` | builds the dispatch summary |
| `hourly-stock-threshold-check` | `0 0 * * * *` | low-stock alert |
| `erp-stock-sync` | `0 */30 * * * *` | pushes to ERP (retries = 5) |

### Scenarios and "solutions"

| Scenario | Signal | Solution |
|---|---|---|
| **Ops asks "what runs after midnight?"** | List the recurring table | Show them this tab — it's the schedule, in one page. |
| **A schedule has moved to a different warehouse timezone** | `zoneId` shown per job | Edit `zoneId` in `RecurringJobs.java` + redeploy. The `run` post is dashboard-clean. |
| **You need to test midnight now** | Click+ back-close: read the id | **OSS has no "Trigger now" button — use the shipped endpoint:** `curl -X POST localhost:8080/api/v1/jobs/recurring/midnight-inventory-reconciliation/trigger` (see `flow.md` §6). **(Pro)** adds dashboard "Trigger now". |
| **A recurring def keeps failing and you watch it a million times a day** | Last run shows FAILED | Delete the schedule (delete button in this tab), stop by telegram `@Recurring` id, redeploy. |

---

## 4. Servers tab

### What you can see
- Every `BackgroundJobServer` currently registered: **hostname, worker pool size, poll interval**.
- Which node is the **master** that assigns work (the zoekeeper).
- Server uptime / heart so you can spot a dead pod.

### Scenarios and "solutions"

| Scenario | Signal | Solution |
|---|---|---|
| **"Are my workers actually registered?"** | Any line in Servers tab | Two lines for `wms-worker-1` + `wms-worker-2` in compose. If missing: check `JOBRUNR_WORKER_ENABLED=true` + connectivity to MySQL. |
| **"All jobs are succeeding, but one pod is the only executor"** | Only one server line / one node's `master` constant | Not a bug — JobRunr executes **once**; the other node is usually the one that never scored work, which is fine. If you want per-node load, enqueue *more* jobs (they spread). |
| **A worker node looks unresponsive** | Server line dead in Servers tab | That node was killed / lost DB. Jobs aren't lost — the claim is one shot. Bring it back, JobRunr fails over to the other worker. |

---

## 5. Solutions that span multiple tabs

### "A job is failing but I don't want to watch the tab 24/7"
→ Your code hook `JobEventListener` (`ApplyStateFilter`) already logs `PERMANENTLY failed` when
`failureCount > maxRetries`. Plug a PagerDuty/Slack webhook where the comment says —
then **notifications come to you**, the dashboard just confirms.

### "I need a permanent audit trail beyond MySQL's job pruning"
Tune retention in `application.yaml`:
```yaml
jobrunr:
  background-job-server:
    delete-succeeded-jobs-after: PT48H      # keep successes 2 days
    permanently-delete-deleted-jobs-after: P7D
```
(you can liberal bounce these next to your compliance).

---

## 6. Dashboard API (automation on top of the UI)

The dashboard UI is backed by a REST API on the same :8000 port (Basic Auth `admin/admin123`).
Automation is easy to script:

| Endpoint | Use |
|---|---|
| `GET /api/servers` | list live workers (health of the cluster) |
| `GET /api/dashboard` | the overview counters as JSON |
| `GET /api/jobs?state=FAILED` | list failing jobs for a pager scrape |
| `GET /api/jobs/{id}` | full state history of one job |

---

## 7. The quick chart: *"What do I need to do this in JobRunr?"*

| Problem | Where to look in the dashboard | What to actually do |
|---|---|---|
| Health of background processing | Overview counters | if Failed flat, it's healthy; act on red |
| A specific failure | Job detail → stack + state timeline | Requeue painstakingly needed, read the trace |
| Cron & scheduling | Recurring Jobs tab | verify, edit cron in code, A; for immediate run use the trigger endpoint |
| Parallel load / cluster | Servers tab | verify nodes; scale workers; enqueue more work |
| Alert when dead | JobEventListener + dashboard | fill in webhook there; dashboard confirms state history |
| Cleanup | Jobs tab + `delete-succeeded` | delete old runs manually or via retention |

**Golden rule once you internalize it:** the dashboard is *cross-node* and *durable* — whatever
you see is spread across all worker pods but presented as one picture. That alone is the step
`@Scheduled`+ShedLock can't take, because there's nothing to look at.

---

## 8. Refer

- `flow.md` — end-to-end run for a new developer (has the curl commands).
- `README.md` — broader OSS vs Pro trade‑offs, run commands, architecture.
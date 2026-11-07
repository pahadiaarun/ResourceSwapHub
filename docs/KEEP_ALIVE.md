# Keeping the Render backend awake

**Problem:** the free Render web service spins down and the next visitor waits ~50 seconds.
**Fix:** an external ping every **10 minutes** to a public health endpoint.
**Status:** implemented in `.github/workflows/keep-alive.yml` (needs one repo variable).

---

## 1. Why an external ping, and why not a scheduler inside the app

Render spins a Free web service down after **15 minutes without inbound traffic** (HTTP requests or
WebSocket messages), and spinning it back up "takes about one minute"
([render.com/docs/free](https://render.com/docs/free)). While the service is spun down there is no
container and no JVM, so nothing inside the application can wake it.

A scheduled task inside the application cannot solve this. This project previously had a
`livenessPing()` heartbeat in `RequestExpiryScheduler` firing every 10 seconds; it only ran while the
JVM was already alive and wrote to in-memory state that Render never sees, so it was removed.
Nothing depended on it.

Inactivity is counted from requests arriving **from outside** the instance, so a self-request loop
would not be reliable either — any restart, deploy or crash that leaves a 15-minute gap ends the
loop permanently, with no external observer to restart it.

## 2. What was added

`.github/workflows/keep-alive.yml` runs on a cron schedule and calls the health endpoint:

| Setting | Value | Why |
| --- | --- | --- |
| Interval | **every 10 minutes** (`*/10 * * * *`) | Render sleeps at 15 min idle; 10 min leaves a 5-minute cushion |
| Per-request timeout | 120 s | covers Render's ~60 s wake-up plus JVM boot |
| Attempts | 3, 20 s apart | absorbs a cold start that returns 502/503 while booting |
| Success test | HTTP 200 **and** `"status":"UP"` | a 200 from the edge alone would not prove the app booted |
| Timeout | 8 min | a stuck run cannot pile up |

It does not need the private network, a token, or any third-party account. `/actuator/health` is
already `permitAll` in `SecurityConfig`, and it is the cheapest endpoint in the app — no database
query, no auth, no session.

## 3. Setup (one step)

Add a repository variable, not a secret — the URL is public:

1. GitHub → the repository → **Settings** → **Secrets and variables** → **Actions** → **Variables**
2. **New repository variable** → name `RENDER_HEALTH_URL`
3. Value: `https://<your-service>.onrender.com/actuator/health`

The service hostname is on the Render dashboard under the service's URL; free services use
`*.onrender.com`. Do not include a trailing slash or a query string.

Then run it once by hand: **Actions** → **Keep-alive** → **Run workflow**. The log prints the HTTP
code and elapsed time per attempt. An elapsed time of 40–60 s means it caught a cold start; a few
hundred milliseconds means the service was already awake.

Until the variable is set the workflow fails immediately with an explicit message rather than
pinging a wrong URL silently.

## 4. Verifying it works

| Check | Expected |
| --- | --- |
| Manual run while the service is asleep | attempt 1 returns 200 in ~40–80 s |
| Manual run while awake | 200 in well under a second |
| Render dashboard → service → Logs | a `GET /actuator/health` roughly every 10 minutes |
| GitHub Actions history | green runs every 10 minutes |
| Browser visit after a long idle period | no 50-second wait |

If runs show multi-second delays but still succeed, the service is still being kept awake — the
delay is GitHub's cron scheduler, not Render.

## 5. Limits you should know

**a. GitHub's cron is best-effort.** Scheduled workflows can be delayed by several minutes at peak
times, and GitHub disables `schedule` triggers in a repository after 60 days with no commits.
That is survivable at a 10-minute interval (delays of up to 5 minutes are absorbed), but it is not a
guarantee.

If you want something stricter, replace this workflow with a dedicated scheduler and keep the same
10-minute interval and URL:

- **cron-job.org** — free, 1-minute granularity, sends a failure email.
- **UptimeRobot** — free 5-minute monitor, doubles as downtime alerting.
- **Better Stack / Healthchecks.io** — free tiers, similar.

Both are more reliable than GitHub's cron and take about two minutes to configure. Delete the
workflow if you switch, so you are not pinging from two places.

**b. Always-on spends almost your whole free allowance.** Render grants **750 free instance hours
per workspace per month**, and spun-down services consume none. A 31-day month is 744 hours, so
keeping this service awake permanently fits — but with only ~6 hours to spare. A second free Render
web service would exhaust the allowance and Render would suspend **all** free services until the
next month. If you add another one, upgrade to a paid instance type (which never spins down) instead
of pinging.

**c. Pinging hides the cold start, it does not remove it.** The first visitor after a gap longer
than 15 minutes still waits. Keep the frontend's "waking the server" state for that case.

**d. This says nothing about correctness.** A green keep-alive only means the process answered
`/actuator/health`; it does not exercise login, listings, chat or the database.

## 6. If the workflow starts failing

The failure message names the likely causes. In order:

1. **Free instance hours exhausted** — Render suspends free services for the rest of the month.
   Check the dashboard banner.
2. **The service is failing to boot** — open Render → Logs. A Flyway migration error or a bad
   environment variable shows up there, not in the GitHub log.
3. **`RENDER_HEALTH_URL` is wrong or the service was renamed** — the log echoes the URL it called.
4. **`/actuator/health` moved behind auth** — the security rules allow it explicitly today; if that
   changes, point the ping at `GET /api/v1/categories` instead.

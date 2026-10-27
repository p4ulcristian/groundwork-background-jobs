# A server that runs no workers declares the metrics itself

> Decided by the agent releasing the composer's switch changes, 2026-10-27, from the user's brief (no workers where jobs don't run: a place where jobs are queued but never run, Iron Rainbow's previews, must be expressible without a fake worker count; the switch's settled "no workers where jobs don't run", S2). Since v0.6.2. Changes in part [Background jobs are counted on the app's meter](2026-10-23-jobs-are-counted-on-the-apps-meter.md) ("a server that adds jobs runs the workers too").

**Decision:** `declare-background-job-metrics!` is public: `(declare-background-job-metrics! meter {:kinds [...] :metric-buckets {...}})` declares on the app's meter what `start-workers!` declares, every key required, checked first. A server that queues background jobs and runs none calls it in place of `start-workers!`; `add-background-job-to-queue!` counts each job it adds there. `:workers` stays at least 1: a server that runs jobs runs at least one.

**Why:** an app's previews run on a copy of the database, where a job that runs would send a copy's emails and pay a copy's payouts; their jobs are queued and wait. Until now the only door to the declaration was `start-workers!`, so a server without workers counted nothing and its meter wrote `:groundwork-metrics/metric-not-declared`; Iron Rainbow reached into `groundwork-background-jobs.metrics`, which is not the api.

**The price:** one more public function. Called beside `start-workers!` on one meter it changes nothing with the same kinds, and is refused by groundwork-metrics with others (`:groundwork-metrics/bad-metrics`, declared before, differently).

**Instead of:**
- **`:workers 0`:** a worker count that means "none" beside every other worker setting, a dispatcher started that never claims
- **a `:run-here?` setting in the library:** whether a server runs jobs is the app's choice of whether to call `start-workers!`; the library has nothing to do on a server that doesn't

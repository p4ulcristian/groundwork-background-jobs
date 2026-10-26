# Every run is given what the app hands in

> The user's decision, 2026-10-26, from Iron Rainbow's port (the background jobs step of groundwork-3). Since v0.6.0.

**What was wrong:** a run's context held the job's transaction, its id, its attempt and its log, nothing of the app's. Iron Rainbow's runs need what its start made once: the place's settings (an email's sender, Stripe's key) and its own queue (a job that queues the next one, an email after a status change). With nothing handed in, a kind's `:run` could only reach them through a global, or be made at the start as a closure over them, a kind per start.

**Decision:** `start-workers!` takes one more required option, `:given-to-every-run`, a map, `{}` for an app with nothing to give. Every run's context holds it as `:given`, the same map each time:

```clojure
(background-jobs/start-workers! pool {…
                                      :given-to-every-run {:settings settings :job-queue job-queue}
                                      …})

(def send-invoice-email
  {:name         :billing/send-invoice-email
   :system-actor :invoice-emails
   :params-shape [:map [:order/id :uuid]]
   :run          (fn [{:keys [transaction given]} {:order/keys [id]}]
                   (send-invoice! (:settings given) transaction id))
   :timeout-ms   60000
   :attempts     5})
```

groundwork-background-jobs never reads the map, and checks only that it is one: `nil` is refused, named, with every other problem at once.

**Why:** a kind stays a value, a `def` made once, the same in every app start and in the tests; what differs between starts comes in where the run is, as the transaction and the log already do. One map keeps the context's keys groundwork-background-jobs's own: an app's names (`:settings`, `:job-queue`) never meet the library's (`:transaction`, `:log`).

**The price:** every caller of v0.5 writes `:given-to-every-run`, `{}` at least. A run reads its app's values one level down, `(:settings given)`.

**Instead of:**
- **the app's keys merged into the context** (`:settings` beside `:transaction`): an app's key could shadow the library's, and a later library key could break an app
- **a kind made at the start** from the settings, a closure: a kind is a different value at each start, and a test makes the app's start to get one
- **a global the run reads** (an atom the start fills): the run's inputs are no longer in its context, and two starts in one JVM (the tests) share one

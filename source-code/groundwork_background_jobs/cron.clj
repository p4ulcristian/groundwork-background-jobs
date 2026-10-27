(ns groundwork-background-jobs.cron
  "Cron lines in UTC, read by cron-utils as Unix cron reads them
   (CronType/UNIX): minute hour day-of-month month day-of-week; *, numbers,
   names (JAN, MON), lists a,b, ranges a-b, steps */n and a-b/n; day of
   week 0 to 7, both Sunday. With both day fields restricted a day is one
   either allows (13th or Friday); one that allows every day (* or */1)
   leaves the other alone.

     (find-next-time-for-cron-line \"0 18 * * *\" now)   ; the next 18:00 UTC"
  (:import
   (com.cronutils.model CronType)
   (com.cronutils.model.definition CronDefinitionBuilder)
   (com.cronutils.model.time ExecutionTime)
   (com.cronutils.parser CronParser)
   (java.time Instant ZoneOffset ZonedDateTime)))

(def ^:private unix-cron (CronDefinitionBuilder/instanceDefinitionFor CronType/UNIX))

(defn- read-execution-time
  "cron-utils's ExecutionTime of a line, or {:problem text}: what
   cron-utils says is wrong with it."
  [line]
  (if-not (string? line)
    {:problem "should be a cron line, text"}
    (try
      {:execution-time (ExecutionTime/forCron (.validate (.parse (CronParser. unix-cron) ^String line)))}
      (catch IllegalArgumentException e
        {:problem (.getMessage e)}))))

(defn- find-next-zoned-time ^ZonedDateTime [^ExecutionTime execution-time ^Instant after]
  (.orElse (.nextExecution execution-time (ZonedDateTime/ofInstant after ZoneOffset/UTC)) nil))

(defn find-problem-in-cron-line
  "nil for a cron line that comes; else what is wrong with it, as text:
   cron-utils's words for one it can't read, or that it never comes (31
   February)."
  [line]
  (let [{:keys [problem execution-time]} (read-execution-time line)]
    (cond
      (not (string? line))                                       problem
      problem                                                    (str "should be a Unix cron line: " problem)
      (nil? (find-next-zoned-time execution-time (Instant/now))) "never comes: no minute fits every field"
      :else                                                      nil)))

(defn find-next-time-for-cron-line
  "The first minute after `after` (an Instant) that a cron line allows,
   UTC. A line that isn't cron, or never comes, throws
   :groundwork-background-jobs/bad-cron."
  ^Instant [line ^Instant after]
  (let [{:keys [problem execution-time]} (read-execution-time line)
        next-time (when execution-time (find-next-zoned-time execution-time after))]
    (when-not next-time
      (throw (ex-info (str "Not a cron line that comes: " (pr-str line) (when problem (str ": " problem)))
                      {:type :groundwork-background-jobs/bad-cron :line line})))
    (.toInstant next-time)))

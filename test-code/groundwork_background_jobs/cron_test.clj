(ns groundwork-background-jobs.cron-test
  "Cron lines read by cron-utils as Unix cron, in UTC: pure, no database."
  (:require
   [clojure.test :refer [deftest is testing]]
   [groundwork-background-jobs.cron :as cron])
  (:import
   (java.time Instant)))

(defn- next-after [line after]
  (str (cron/find-next-time-for-cron-line line (Instant/parse after))))

(deftest the-next-time-a-cron-line-allows
  (is (= (Instant/parse "2026-10-22T18:00:00Z")
         (cron/find-next-time-for-cron-line "0 18 * * *" (Instant/parse "2026-10-22T10:00:00Z"))))
  (is (= (Instant/parse "2026-10-23T01:00:00Z")
         (cron/find-next-time-for-cron-line "0 1 * * *" (Instant/parse "2026-10-22T10:00:00Z"))))
  (testing "after a time: the next minute, never the same one"
    (is (= "2026-10-28T10:30:00Z" (next-after "30 10 * * *" "2026-10-27T10:30:00Z")))
    (is (= "2026-10-28T10:30:00Z" (next-after "30 10 * * *" "2026-10-27T10:30:59.999Z")))
    (is (= "2026-10-27T10:30:00Z" (next-after "30 10 * * *" "2026-10-27T10:29:30.5Z"))))
  (testing "in UTC"
    (is (= "2026-10-25T02:30:00Z" (next-after "30 2 * * *" "2026-10-25T00:00:00Z"))
        "the night Europe's clocks go back: once, at 02:30 UTC")))

(deftest the-lines-the-old-parser-read-are-read-the-same
  ;; 2026-10-27, Tuesday
  (let [from "2026-10-27T10:07:00Z"]
    (is (= "2026-10-27T10:15:00Z" (next-after "*/15 * * * *" from)) "a step over *")
    (is (= "2026-10-27T10:20:00Z" (next-after "5/15 * * * *" from)) "a step from a number: 5, 20, 35, 50")
    (is (= "2026-10-27T10:10:00Z" (next-after "1-5,10-15/2 * * * *" from)) "a list of a range and a stepped range")
    (is (= "2026-11-01T00:00:00Z" (next-after "0 0 1,15 * *" from)) "a list of days")
    (is (= "2026-10-31T00:00:00Z" (next-after "0 0 1-31/10 * *" from)) "1, 11, 21, 31")
    (is (= "2026-11-01T00:00:00Z" (next-after "0 0 * * 0" from)) "Sunday is 0")
    (is (= "2026-11-01T00:00:00Z" (next-after "0 0 * * 7" from)) "and 7")
    (is (= "2026-10-30T00:00:00Z" (next-after "0 0 * * 5-7" from)) "Friday to Sunday")
    (is (= "2027-01-01T00:00:00Z" (next-after "0 0 1 */12 *" from)) "a step of the whole year: January")
    (is (= "2028-02-29T00:00:00Z" (next-after "0 0 29 2 *" from)) "29 February, in a leap year")
    (is (= "2026-10-28T00:00:00Z" (next-after "  0\t0  * * *  " from)) "spaces and tabs between and around the fields")))

(deftest a-day-is-one-either-day-field-allows-when-both-are-restricted
  ;; 2026-10-27 is a Tuesday; 30 October a Friday, 13 November a Friday
  (let [from "2026-10-27T10:00:00Z"]
    (is (= "2026-10-30T00:00:00Z" (next-after "0 0 13 * 5" from)) "the 13th or a Friday: Friday 30 October")
    (is (= "2026-11-13T00:00:00Z" (next-after "0 0 13 * *" from)) "the 13th only")
    (is (= "2026-10-30T00:00:00Z" (next-after "0 0 * * 5" from)) "Fridays only")
    (is (= "2026-10-29T00:00:00Z" (next-after "0 0 */2 * 1" from)) "*/2 is restricted: an odd day or a Monday")
    (is (= "2026-10-28T00:00:00Z" (next-after "0 0 1-31 * 1" from)) "1-31 is restricted too: every day")
    (is (= "2027-02-01T00:00:00Z" (next-after "0 0 29 2 1" from)) "29 February or a Monday in February")
    (testing "*/1 allows every day, as *: the other field alone (the old parser read it as restricted)"
      (is (= "2026-11-02T00:00:00Z" (next-after "0 0 */1 * 1" from)) "Mondays")
      (is (= "2026-11-13T00:00:00Z" (next-after "0 0 13 * */1" from)) "the 13th"))))

(deftest the-lines-read-otherwise-than-before
  (let [from "2026-10-27T10:00:00Z"]
    (testing "names, which the old parser refused"
      (is (= "2026-10-28T00:00:00Z" (next-after "0 0 * * MON-FRI" from)))
      (is (= "2027-01-01T00:00:00Z" (next-after "0 0 1 JAN *" from))))
    (testing "a step from 7 in the day of week is a step from Sunday, 0 (the old parser read 7/n as Sunday alone)"
      (is (= "2026-10-28T00:00:00Z" (next-after "0 0 * * 7/3" from)) "0, 3, 6: Wednesday 28 October"))
    (testing "a step longer than its field, which the old parser read as the first value only, is refused"
      (doseq [line ["*/60 * * * *" "0 */24 * * *" "0 0 */40 * *" "0 0 * */13 *" "0 0 * * */8"]]
        (is (some? (cron/find-problem-in-cron-line line)) line)))))

(deftest a-line-that-is-not-cron-or-never-comes-is-named
  (is (nil? (cron/find-problem-in-cron-line "0 18 * * *")))
  (doseq [line ["every day" "0 18 * *" "0 18 * * * *" "60 0 * * *" "0 0 0 * *" "0 0 * * 8" "0 0 5-1 * *" "0 0 */0 * *"
                "@daily" "0 0 L * *" "0 0 ? * 1" "0 0 * * 1#2" ""]]
    (is (re-find #"^should be a Unix cron line: " (cron/find-problem-in-cron-line line)) (pr-str line)))
  (is (= "should be a cron line, text" (cron/find-problem-in-cron-line nil)))
  (is (= "never comes: no minute fits every field" (cron/find-problem-in-cron-line "0 0 31 2 *")))
  (is (= "never comes: no minute fits every field" (cron/find-problem-in-cron-line "0 0 31 4,6,9,11 *")))
  (is (= :groundwork-background-jobs/bad-cron
         (:type (ex-data (try (cron/find-next-time-for-cron-line "0 0 30 2 *" (Instant/now)) (catch Exception e e))))))
  (is (= :groundwork-background-jobs/bad-cron
         (:type (ex-data (try (cron/find-next-time-for-cron-line "every day" (Instant/now)) (catch Exception e e)))))))

# Cron lines are read by cron-utils, as Unix cron reads them

> The user's decision, 2026-10-27 (keep groundwork minimal; take a library only where the problem is one people get subtly wrong, schedules among them, and its cost is small where it lands; cron-utils with its UNIX definition, schedules in UTC as decided). Since v0.6.1. Changes in part [Every value that sets behaviour is a setting](2026-10-22-every-value-that-sets-behaviour-is-a-setting.md): its fixed bound of 600000 steps on the search for a line's next time.

**Decision:** a schedule's cron line is parsed, checked and searched by cron-utils (`com.cronutils/cron-utils` 9.2.1), with its Unix definition (`CronType/UNIX`): five fields, minute hour day-of-month month day-of-week; `*`, numbers, names (`JAN`, `MON`), lists, ranges and steps; day of week 0 to 7, both Sunday. The next time is cron-utils's `nextExecution` after now, in UTC, as decided: still no time zone.

- **The day fields:** with both restricted, a day is one either allows: `0 0 13 * 5` runs on the 13th and on every Friday. A field that allows every day, `*` or `*/1`, leaves the other alone: `0 0 */1 * 1` is Mondays. `*/2` and `1-31` are restricted.
- **Checked at the start, every bad line named at once:** a schedule's `:cron` is part of `start-workers!`'s options shape, so a line cron-utils can't read is `:groundwork-background-jobs/bad-options` with cron-utils's words, beside every other problem, and a line that never comes (`0 0 31 2 *`) is named so. `find-next-time-for-cron-line` still throws `:groundwork-background-jobs/bad-cron` for either.
- **No search bound of groundwork-background-jobs's own:** cron-utils finds the next time, or that there is none.

**Why:** the next time of a cron line is a search with corner cases people get wrong (month lengths, 29 February, the day fields' either-or, Sunday as 0 and 7). cron-utils is the cron library on the JVM, read and fixed by many; 175 KB, and its one dependency, slf4j-api, is on the path already through groundwork-postgres.

**The price:** lines read otherwise than v0.6.0 read them, each in the tests:

- **`*/1` in a day field** allows every day, as `*`, so the other day field alone decides: `0 0 */1 * 1` was every day (either field), and is Mondays (Vixie cron reads it so too);
- **a step from 7 in the day of week** is a step from Sunday as 0: `0 0 * * 7/3` was Sunday alone, and is Sunday, Wednesday and Saturday; `7/1` was Sunday alone, and is every day. Vixie cron reads `7/3` as Sunday alone;
- **a step longer than its field** (`*/60` minutes, `*/24` hours, `*/40` days, `*/13` months, `*/8` days of week) was the field's first value only, and is refused;
- **names** (`MON-FRI`, `JAN`) were refused, and are read.

A line read the same in both, its next time is the same: 60000 random lines of numbers, lists, ranges and steps, each from a random time in 2023 to 2036, gave the same next time (or none) in both, but for the three kinds above. A schedule's key is still its kind, its line's text and its params, so a deploy that takes v0.6.1 keeps its schedules' rows.

**Instead of:**
- **the hand-written parser and search**: each corner case its own code, read by nobody else
- **cron-utils's Quartz or Spring definitions**: seconds, `?`, `L`, `W` and `#`: a line an app copies from a Unix crontab would mean something else
- **refusing `*/1` and `7/n` in a day field**: rules of groundwork-background-jobs's own on top of the library's, for lines nobody writes

# Ortaq 2.0 test matrix

This matrix is the evidence map for `PLAN_V2.md`. Automated tests use fake bot credentials and isolated H2 unless a disposable PostgreSQL staging copy is explicitly named. The V2-00-02 PostgreSQL test is opt-in and must not run without owner approval. Mockito/fake Telegram client captures sends; no ordinary unit/integration test contacts Telegram. For bugs, commit a failing regression test first, then the fix. `Telegram` means a **human** runs a separate development bot/group and records date, result and relevant message IDs without tokens or private content. Actual results are recorded in the plan and release guide.

| Task | Automated test first / decisive assertions | Telegram manual check |
| --- | --- | --- |
| V2-00-01 | Baseline full Maven suite; record failures, legacy command/schema count and old ledger sum | Read commands and backup procedure |
| V2-00-02 | Empty H2 plus Hibernate `validate`; populated Hibernate-created legacy H2 fixture retains IDs, expense/share totals, balances and cooking rows across repeated migration; partial schema refused; failed migration rolls back its ledger write | Only after owner approval, run the guarded PostgreSQL staging rehearsal in `ORTAQ_V2_RELEASE.md`; compare counts, balances, migration history and Hibernate validation. H2 success alone does not close this task. |
| V2-00-03 | Reject nonmember, cross-household ID, private write; retain existing registrations | Try two groups and private chat |
| V2-01-01 | Numeric group text produces zero expense; `/shygyn` produces one | Compare `/tarih` before/after both |
| V2-01-02 | Valid/invalid expense, settlement, undo, history lazy payer, report and balance invariants | Exercise seven finance commands |
| V2-01-03 | Week/day off/swap/clear, ambiguous name, lazy cook, duplicate swap | Schedule and swap with two users |
| V2-01-04 | List/note/chore happy, empty, bad ID, detached relation and author/household denial | Seven commands in two groups |
| V2-01-05 | Deadline month end and invalid day, empty random, command help coverage and private rules | Run each helper valid/invalid |
| V2-02-01 | Callback sender/chat mismatch, duplicate update/callback, command still routes | Repeat and forward a button |
| V2-02-02 | Prompt reply binds user/chat/message ID; unrelated number ignored; expiry/restart | Price prompt with other user and restart |
| V2-02-03 | Six menus and all branches, Back/Cancel, stale token, localization | Browse every menu |
| V2-02-04 | Two users share menu but distinct sessions, double confirm once | Two simultaneous taps |
| V2-03-01 | Complete catalog, Kazakh labels, page cap, custom/unknown key | Browse categories |
| V2-03-02 | Price boundaries, custom name, edit/remove and exact total, cancel/expiry no ledger | Build/edit/cancel basket |
| V2-03-03 | Default shared/all, chosen subset includes payer, personal no shares, foreign member rejected | Toggle and choose members |
| V2-03-04 | Atomic rollback on injected failure, concurrent/double confirm one expense, shares sum, personal zero ledger | Confirm both types, check finance |
| V2-03-05 | Recent five distinct confirmed picks; receipt fields; photo wrong user/type/size; file ID isolation | Attach photo, reopen receipt |
| V2-03-06 | `/satyp` no expense; linked list mark once; void own receipt restores balance, nonbuyer denied | List mark and void |
| V2-03-07 | `/tolem` and button both pending; recipient only accept; reject/expire; concurrent accept once; correction bilateral; old settlements unchanged | Request, deny, accept twice, compare debt |
| V2-04-01 | Effective week after swap, day off, empty schedule | Inspect week menu |
| V2-04-02 | Scheduled snapshot, multiple actual, confirmer distinct, unique date/occasion, correction audit | Inspect dev event |
| V2-04-03 | Noncook confirmer does not become cook, helper selection, no schedule, duplicate ready once | Ready by noncook, repeat tap |
| V2-04-04 | Original confirmer/manager edits; outsider denied; before/after history | Correct event |
| V2-04-05 | 18:30 due only effective cooks; ready suppresses; quiet hours and restart once | Near-time dev reminder |
| V2-05-01 | Event uniqueness, response upsert, wrong group, closed/expired denial, duplicate update | Two users change response |
| V2-05-02 | Tea/food three choices, food save-some, linked ready call, no duplicate announce | Send tea and food |
| V2-05-03 | Store need goes to existing list, close rejects, no expense, `/tizim` still works | Announce/add/close |
| V2-05-04 | FIFA/movie/walk/custom validation; guest time/ack, discussion ordinary chat | Play and guest flows |
| V2-05-05 | Selected mentions only; DM requires start+opt-in; per-user/household caps, quiet suppression | Opt in/out, rate and quiet test |
| V2-06-01 | Old household defaults, election/transfer, nonmanager denial, optimistic edit | Two users manage settings |
| V2-06-02 | Menu parity with `/tizim`, notes, chores; pagination, empty, authorization | Add/list/delete via menu |
| V2-06-03 | Report counts shared receipt once; settlement/personal excluded; deadline/random parity | Compare button and commands |
| V2-06-04 | Presence change/clear, 12h expiry, no GPS/history, outsider denied | Set and clear |
| V2-06-05 | One poll/date/household, response update, expiry and isolation | Two users vote twice |
| V2-06-06 | Durable unique delivery across restart; deadline 3/1/0; night off; old digest cutover; quiet skip | Observe staged sends once |
| V2-07-01 | Human visual checklist against supplied reference and rights | Approve image |
| V2-07-02 | Official current sticker dimensions/size/format and manifest/contact sheet validation | Review and upload pack |
| V2-07-03 | Event-to-ID mapping, missing ID fallback, throttling, duplicate suppression | Observe sparse sends |
| V2-08-01 | Restore legacy fixture; before/after count and balance checks; rollback and no double accounting | Reconcile staging ledger |
| V2-08-02 | Parallel callbacks/updates, forwarded stale message, foreign IDs, photo ID leak, restart | Two groups/two users abuse cases |
| V2-08-03 | Full Maven suite plus recorded manual case sheet; no pending required flow | Complete all rows |
| V2-08-04 | CI, `git diff --check`, migration/restore rehearsal, postdeploy balance comparison | Human signs off cutover |

## Cross-cutting fixtures and invariants

Use households H1 and H2 with overlapping member display names but distinct Telegram user/chat IDs. Include a user in both households, a removed/nonmember user, a private-chat-only user, 0.01 ₸, 100,000,000.00 ₸, invalid 3-decimal and negative prices, a 31-day deadline in February, and a group message `630 су`. For every active nondeleted shared expense, `sum(ExpenseShare.shareAmount) = Expense.amount`; household net balances sum to zero. A purchase creates at most one linked expense and is never independently aggregated; a personal purchase creates no ledger entry. A pending/rejected repayment has no settlement expense; a confirmed request has exactly one. An old settlement stays intact after migration. Tests must flush and clear the JPA persistence context before reading history, chores and notes so lazy-loading bugs become visible. Use concurrent transactions or latches for confirmation races, not sequential double calls alone.

Check each callback with valid and invalid combinations of user, household, event status, version and permission. Check a reply to the wrong bot prompt, a plain group number, a repeated update ID, a repeated callback ID, an expired session, and a restart between draft and confirmation. Check Telegram send failures separately from DB commits; assert no second financial write on retry. For scheduled sends, assert durable event keys survive restart and quiet-hour policy skips rather than sends a burst later. For group social events, response replacement must leave one response row. For private urgent sends, test both opted-in and never-started users. For photo attachments, assert no raw file ID in logs or foreign-household output.

## Manual result record template

`Task ID | date/time Asia/Almaty | dev bot alias (no token) | household test alias | scenario | PASS/FAIL/PENDING | bot message ID or brief evidence | issue/fix SHA`. Keep this table in a private test record or append sanitized results here during V2-08-03. Do not mark a Telegram test PASS from a unit test. A PENDING result is a release blocker unless the optional sticker exception is explicitly recorded in the plan.

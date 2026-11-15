# Ortaq 2.0 safe release guide

This is a checklist for a later implementation release. **No production migration, deployment or Telegram test was done during planning.** Production data is on the existing Neon PostgreSQL instance. The owner performs credential, backup, BotFather and production approval steps; never put secrets in Git or test output.

## V2-00-01 baseline audit (implementation preflight)

On 15 November 2026, `dev` was clean before this audit. `./mvnw.cmd -q test` completed with **124 tests in 31 suites, 0 failures, 0 errors, 0 skipped** using fake bot credentials and the test profile's in-memory H2. The initial sandbox run could not download Maven; the approved network retry passed. This is an automated baseline only; no dev Telegram command or Neon backup/restore was verified **by the agent**. No schema or ledger write was made by the agent for this audit.

### Owner-reported Neon backup and restore verification

The owner reported the following manual verification on 15 November 2026. The agent did not connect to either database, run the backup/restore commands, inspect the archive, or independently check the SQL outputs.

| Check | Owner-reported result |
| --- | --- |
| Source | Neon project `ortaq-bot`, branch `ortaq-backup-check-20261115`, database `neondb` |
| Isolated target | Separate Neon project `ortaq-restore-check`, database `ortaq_restore_check` |
| Archive | `ortaq-backup-20261115.dump`, 30,789 bytes; archive contents and credentials remain outside Git |
| Tools and operation | PostgreSQL `pg_dump`/`pg_restore` 18.7; restore completed successfully |
| Schema/rows | All ten original tables restored; row counts matched (individual counts not supplied) |
| Active ledger totals | Expenses: 2067.00 in both databases; shares: 2067.00 in both databases |
| Member balances | All three matched exactly: -660.66, +1332.38, -671.72; sum is zero |
| Integrity | Expense/share mismatches: 0; cross-household payer issues: 0; cross-household share issues: 0 |
| Production | Owner reports no production modification |

This verifies the backup/restore and reported financial comparison for V2-00-01. **Separate development-bot Telegram checks and staging-data sanitization remain pending.** Keep the restored staging project private until direct identifiers and free text are sanitized; its financial records remain sensitive even afterward. Do not treat either pending step as completed or run V2 migrations yet.

The 27 current commands route as follows. `/start` and `/komek` work in private chat; the other handlers require a group. These are descriptions of current code, not V2 acceptance claims.

| Command | Current handler | Current effect |
| --- | --- | --- |
| `/start` | `StartHelpHandler` | Greeting |
| `/komek` | `StartHelpHandler` | Static command help |
| `/shygyn` | `ExpenseHandler` | Parses and posts a shared expense, optionally with mentions |
| `/balans` | `BalanceHandler` | Member net balances |
| `/qaryz` | `BalanceHandler` | Simplified transfers |
| `/tolem` | `RepaymentHandler` | Immediately posts a settlement expense and share; V2 must change this to pending confirmation |
| `/oshiru` | `HistoryHandler` | Soft-deletes sender's last or selected owned ledger entry |
| `/tarih` | `HistoryHandler` | Last ten active expenses/settlements |
| `/esep` | `ReportHandler` | Current or numbered month, excluding settlements |
| `/kezek` | `CookingHandler` | Effective today and seven-day schedule |
| `/kezek_qoyu` | `CookingHandler` | Replaces selected weekday schedule rows |
| `/kezek_tazalau` | `CookingHandler` | Clears weekly schedule |
| `/otkizu` | `CookingHandler` | One-day handover from current cook |
| `/dayyn` | `CookingHandler` | Announces sender as cook without a cooking record; V2 must ask who cooked |
| `/tizim` | `ShoppingHandler` | Shows or adds to-buy items |
| `/satyp` | `ShoppingHandler` | Marks a to-buy item bought, no expense |
| `/jazba` | `NoteHandler` | Shows or adds notes |
| `/jazba_oshiru` | `NoteHandler` | Soft-deletes a household note by ID; current code does not check author |
| `/is` | `ChoreHandler` | Records a completed chore |
| `/ister` | `ChoreHandler` | Shows recent chores |
| `/songy` | `ChoreHandler` | Finds last chore matching text |
| `/merzim` | `DeadlineHandler` | Lists active recurring payment dates |
| `/merzim_qosu` | `DeadlineHandler` | Adds a monthly payment deadline |
| `/merzim_oshiru` | `DeadlineHandler` | Deactivates a deadline by household ID; current code does not check creator |
| `/kim` | `RandomHandler` | Picks a registered member |
| `/tandau` | `RandomHandler` | Picks from 2–10 options |
| `/tiyn` | `RandomHandler` | Coin toss |

`HomeTelegramBot` also calls `ExpenseHandler.handlePlainExpense` for any digit-leading group text, outside the command map. It registers/refreshes a member for every text message in a group and ignores callback and non-text updates. `CommandRouter` catches handler exceptions and sends a generic error. Current lazy relation access in history, notes and chores happens after service return and needs regression tests in V2-01. `DigestJob` polls every 15 minutes and persists `household.last_digest_date` after sending; it does not use a separate delivery table yet. The current `README.md` and `DEPLOY.md` still advertise plain-number expenses; V2-01-01/V2-08-04 will correct that when code behavior changes.

The legacy schema has exactly ten JPA tables: `household` (unique `telegram_chat_id`, `last_digest_date`), `member` (unique `(household_id,telegram_user_id)`), `expense` (`amount NUMERIC(12,2)`, `deleted`, `settlement`, payer/household FKs), `expense_share` (`share_amount NUMERIC(12,2)`, expense/member FKs), `cooking_schedule` (unique household/day/member), `cooking_swap` (unique household/date/from-member), `shopping_item`, `note`, `chore_log`, and `payment_deadline`. The last four have household relationships and soft/bought/active flags as appropriate. Dev uses file H2 `./data/homebot`; tests use in-memory H2; production uses PostgreSQL with `ddl-auto=update`. There is no migration history yet. No V2 table should replace these old rows or IDs. The ledger rule is `balance = active amount paid - active share amount owed`; an active settlement participates in balances but is excluded from monthly expense reports. Every active expense should have shares totaling its amount; household balances should sum to zero.

### Read-only PostgreSQL preflight queries

Run against a **sanitized staging copy** first, then against production only as a human-authorized read-only preflight. Save only aggregate results and opaque IDs in a private location. The SQL below does not mutate data; verify schema names against the actual Neon copy before trusting it. Run before backup, after restore, and after any migration, comparing results for the same snapshot. New business writes between snapshots require reconciliation rather than a false equality claim.

```sql
BEGIN READ ONLY;
SELECT 'household' AS table_name, count(*) AS rows FROM household
UNION ALL SELECT 'member', count(*) FROM member
UNION ALL SELECT 'expense', count(*) FROM expense
UNION ALL SELECT 'expense_share', count(*) FROM expense_share
UNION ALL SELECT 'cooking_schedule', count(*) FROM cooking_schedule
UNION ALL SELECT 'cooking_swap', count(*) FROM cooking_swap
UNION ALL SELECT 'shopping_item', count(*) FROM shopping_item
UNION ALL SELECT 'note', count(*) FROM note
UNION ALL SELECT 'chore_log', count(*) FROM chore_log
UNION ALL SELECT 'payment_deadline', count(*) FROM payment_deadline
ORDER BY table_name;

SELECT household_id,
       count(*) AS expense_rows,
       count(*) FILTER (WHERE NOT deleted) AS active_rows,
       count(*) FILTER (WHERE deleted) AS deleted_rows,
       count(*) FILTER (WHERE settlement) AS settlement_rows,
       coalesce(sum(amount) FILTER (WHERE NOT deleted), 0) AS active_amount
FROM expense GROUP BY household_id ORDER BY household_id;

SELECT e.household_id, coalesce(sum(s.share_amount), 0) AS active_shares
FROM expense e JOIN expense_share s ON s.expense_id = e.id
WHERE NOT e.deleted GROUP BY e.household_id ORDER BY e.household_id;

SELECT e.id, e.household_id, e.amount, coalesce(sum(s.share_amount), 0) AS shares
FROM expense e LEFT JOIN expense_share s ON s.expense_id = e.id
WHERE NOT e.deleted GROUP BY e.id, e.household_id, e.amount
HAVING e.amount <> coalesce(sum(s.share_amount), 0)
ORDER BY e.household_id, e.id;

WITH paid AS (
  SELECT household_id, payer_id AS member_id, sum(amount) AS total
  FROM expense WHERE NOT deleted GROUP BY household_id, payer_id
), owed AS (
  SELECT e.household_id, s.member_id, sum(s.share_amount) AS total
  FROM expense e JOIN expense_share s ON s.expense_id = e.id
  WHERE NOT e.deleted GROUP BY e.household_id, s.member_id
), balances AS (
  SELECT m.household_id, m.id AS member_id,
         coalesce(p.total, 0) - coalesce(o.total, 0) AS balance
  FROM member m
  LEFT JOIN paid p ON p.household_id = m.household_id AND p.member_id = m.id
  LEFT JOIN owed o ON o.household_id = m.household_id AND o.member_id = m.id
)
SELECT household_id, count(*) AS members, sum(balance) AS net_balance
FROM balances GROUP BY household_id ORDER BY household_id;
COMMIT;
```

The mismatch query should return no rows and each household's `net_balance` should be zero. If not, preserve the evidence and investigate before migration; do not adjust records to make the check pass. To detect cross-household references, also inspect joins from `expense.payer_id` and `expense_share.member_id` to `member.household_id`; these FKs guarantee existence but not household equality. Existing rows must remain unchanged even if an anomaly is discovered.

### Backup and restore rehearsal procedure

The human records the source branch/database identity, timestamp, tool version, backup location, encryption and retention outside Git. Use either a Neon snapshot restored to a **new branch** (never the live branch) or a consistent custom-format `pg_dump` restored into a distinct empty staging database. [Neon describes snapshot-to-new-branch recovery](https://neon.com/blog/announcing-neon-snapshots-a-smoother-path-to-recovery); [PostgreSQL documents custom dumps](https://www.postgresql.org/docs/current/app-pgdump.html) and [single-transaction restore](https://www.postgresql.org/docs/current/app-pgrestore.html). Verify that the available Neon plan supports the chosen snapshot/restore workflow before relying on it.

For a dump, the human supplies connection values privately to the PostgreSQL tools, directs the archive **outside this repository**, and checks `pg_dump` exit code and archive size. With a newly created empty staging target whose endpoint and database name have been independently verified as **not production**, run `pg_restore --no-owner --no-privileges --single-transaction --exit-on-error --dbname=<staging connection> <archive>`; do not use `--clean` or `--create`. If restore fails, keep production untouched and diagnose the staging target. Run the preflight queries on the restored copy, compare all table counts, active amounts/shares and balances to the source snapshot, and record the result. A successful backup file alone is not a verified restore. Do not publish raw production data or connection strings. If a staging copy is sanitized after restore, compare the unsanitized aggregate snapshot first, then use only the sanitized copy for development tests.

## Before implementation and staging

- [ ] Confirm current `dev` branch, clean worktree, CI green and complete V2 task/test matrix. Use a separate development bot/token and database; only one polling instance per token.
- [ ] Human creates a Neon backup/snapshot or consistent `pg_dump` and records timestamp, database identity and retention outside the public repo. Verify a **restore into a disposable database**, not just that a backup file exists.
- [ ] Record read-only preflight per household: `household`, `member`, `expense`, `expense_share`, `cooking_schedule`, `cooking_swap`, `shopping_item`, `note`, `chore_log`, `payment_deadline` row counts; active/deleted/settlement expense counts; sum of active expense amounts; sum of active shares; balance of each member and household zero-sum. Do not print names or credentials in public logs.
- [ ] Run the same versioned additive migrations against a sanitized staging copy. Confirm all old rows/IDs and totals unchanged. Start the new app with prod-like `ddl-auto=validate`. Test the old artifact against the expanded schema as an application rollback rehearsal.
- [ ] In dev Telegram, manually complete the matrix with two groups and two users, including number-chat regression, confirmation, duplicate clicks, stale buttons, reboot, cross-household isolation, reminders and private opt-in. Record results, not just intent. Verify current host/provider limits before scheduling the rollout.

## Production cutover — human authorized only

- [ ] Choose a quiet window and stop the existing poller before starting a new instance with the same production token. Confirm no second Render service/local session is polling.
- [ ] Human checks a fresh backup/restore point. Deploy the approved `dev` commit/artifact to the existing Render service according to [`DEPLOY.md`](DEPLOY.md), after explicit owner authorization. Migrations must be additive; no schema drop, database reset, H2 path use or blanket cleanup.
- [ ] On startup, confirm migration versions and validation, `/health` 200, exactly one poller, no Telegram 409, and no database connection errors. Compare row counts and historical ledger totals to the preflight snapshot before allowing new V2 writes.
- [ ] Verify `/start`, menu, `/komek`, `/shygyn`, `/balans`, `/qaryz`, `/tarih`, weekly schedule, to-buy list, one shared and one personal purchase, and recipient-confirmed repayment with consenting test users. Do not create artificial financial records in a real household; use an authorized isolated production test group and inspect its own data only.
- [ ] Watch logs and health for at least one full reminder cycle: callback errors, duplicate keys, failed deliveries, scheduler backlog, DB pool, Telegram rate limits and 409 conflicts. Compare new purchase/expense links and zero-sum balances. Never log token, photo file ID or full message text.

## Rollback and reconciliation

If a startup or behavior check fails, stop the new poller and redeploy the last known compatible artifact with the same token. Leave additive V2 tables in place; the old artifact must have been tested against them. Disable V2 menus and new scheduled jobs by configuration if needed while investigating. Do not automatically reverse confirmed ledger entries or replay Telegram callbacks. If the migration itself corrupted data, stop writes, export all post-snapshot transactions, restore the verified snapshot to a **new** Neon database, reconcile post-snapshot ledger rows with humans, then switch the service's database configuration only after owner approval. Never overwrite the existing Neon database blindly. Recheck balances and row counts before resuming. Record incident, affected households and rollback commit without secrets.

## Human-only tasks and launch notes

- [ ] Owner supplies/rotates BotFather token and private environment variables, maintains a separate dev bot, approves production deployment and any restore, and tests actual Telegram interaction. The agent never requests a real token or runs the live bot.
- [ ] The owner has approved the [official Ақтөс reference](img/aktos-reference.jpg), generated with Gemini, for this public repository. Preserve it unchanged; the owner still reviews derived assets, uploads any future sticker pack and configures IDs. Core bot launch may proceed without sticker assets only if V2-07 is recorded as a named release exception with owner and due date; no sticker task is falsely checked.
- [ ] Owner reviews Kazakh copy and redacted screenshots, updates README live URL when known, and checks Neon/Render current plan limits and backup retention. Existing `docs/DEPLOY.md` currently contains obsolete instructions to use bare numeric group messages and disable privacy for that purpose; V2-08-04 must replace those statements before launch.

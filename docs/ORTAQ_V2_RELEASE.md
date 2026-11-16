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

This records the owner-reported backup/restore and financial comparison for V2-00-01. The later staging and Telegram results are recorded below. The agent did not independently connect to Neon or Telegram.

### Owner-reported staging and dev Telegram checks, 16 November 2026

The owner reports running the SQL script below against the **isolated** Neon database `ortaq_restore_check`; it ended in `COMMIT`. Post-sanitization checks showed 3 members, 13 expenses and 37 expense shares. Active expense and share totals both remained 2067.00. The script's in-transaction row-count, individual-balance, expense/share and zero-sum checks passed. The owner reports that original production was not modified. These are human-reported results; the agent did not inspect either database. The script compares all ten table counts within the transaction, while the owner supplied individual post-commit counts for only three tables.

The owner created a distinct BotFather bot (`Ortaq Dev`), removed the production bot from the separate test group, and started the existing application using the `dev` profile and an isolated H2 file. Reported results:

| Check | Result |
| --- | --- |
| `/start` | Pass |
| `/komek` | Pass; help text displays all 27 legacy commands |
| `/balans` in group | Pass; registered tester shows 0 ₸ |
| `/balans` in private | Pass; group-only usage rejected |
| `/kezek` | Pass; empty weekly schedule shown |
| `/tizim` | Pass; empty shopping list shown |
| Telegram `/` command picker | Pass; owner verified the legacy command list in private chat and the `ortaq test` group |

The owner reported the final command-picker pass on 16 November 2026. With the earlier backup/restore, staging sanitization, baseline test suite and dev-bot command checks, V2-00-01 acceptance and manual criteria are complete. This remains human-reported Telegram and Neon evidence; the agent did not run either system. V2-00-02 is the next task, pending a separate implementation request.

### Isolated staging sanitization procedure (human only)

The reviewed [SQL script](sql/ORTAQ_V2_SANITIZE_STAGING.sql) matches the ten current JPA entities. It replaces `household.telegram_chat_id/title`, `member.telegram_user_id/display_name/telegram_username`, and the free text in `expense.description`, `shopping_item.text`, `note.text`, `chore_log.text`, and `payment_deadline.title`. It leaves all primary/foreign keys, dates, flags, amounts, shares and deadline settings intact. The script refuses any database name except `ortaq_restore_check`, validates counts and the complete ledger inside one transaction, and commits only after validation. Database name alone cannot prove Neon **project** identity: the human must confirm the separate `ortaq-restore-check` project's endpoint and use only its credentials. Do not run this script against the source project, backup branch, or original dump. Keep the staging project and its snapshots private: old row versions and backups may still contain original text, and financial patterns can remain identifying.

From PowerShell in the `dev` checkout, using PostgreSQL client tools already installed:

1. In the Neon console, select the **separate project** `ortaq-restore-check`, database `ortaq_restore_check`. Copy its host and database **user name** into local PowerShell variables; do not paste the connection string or password into chat or Git. Confirm its host differs from the `ortaq-bot` project's host. Keep the original dump outside the repository.
2. Run the commands below. `psql -W` prompts for the staging password; no password is stored in the command or environment. Inspect the first result. Stop if the database or host is unexpected. Run the SQL only after this check.

   ```powershell
   $stageHost = Read-Host 'Host of the separate ortaq-restore-check Neon project'
   $stageUser = Read-Host 'User of the separate staging database'
   $env:PGSSLMODE = 'require'
   $env:PGCONNECT_TIMEOUT = '10'
   psql -X -v ON_ERROR_STOP=1 -W -h $stageHost -U $stageUser -d ortaq_restore_check -c 'SELECT current_database() AS database, current_user AS role, inet_server_addr() AS server_address;'
   if ($LASTEXITCODE -ne 0) { throw 'Staging preflight failed; stop here.' }
   psql -X -v ON_ERROR_STOP=1 -W -h $stageHost -U $stageUser -d ortaq_restore_check -f .\docs\sql\ORTAQ_V2_SANITIZE_STAGING.sql
   if ($LASTEXITCODE -ne 0) { throw 'Sanitization failed; verify rollback before retrying.' }
   ```

3. Successful output must end with `COMMIT`. If an error occurs before `COMMIT`, the psql connection closes and PostgreSQL rolls back the transaction. In an interactive psql session, issue `ROLLBACK;` before disconnecting. [PostgreSQL documents `ON_ERROR_STOP` and psql exit behavior](https://www.postgresql.org/docs/current/app-psql.html). **Do not retry blindly**; determine the cause, restore a fresh disposable staging database from the original archive if needed, then repeat the host check. If a later check finds a problem after commit, recreate staging from the untouched original dump; pseudonyms cannot be reversed from this script.
4. Open staging with `psql -X -v ON_ERROR_STOP=1 -W -h $stageHost -U $stageUser -d ortaq_restore_check`, paste the full **Read-only PostgreSQL preflight queries** block below, then `\q`. Compare with the recorded pre-sanitization result: ten table counts, 2067.00 active expenses and shares, all three balances (-660.66, +1332.38, -671.72), zero expense/share mismatches, zero cross-household payer/share issues, and household zero sum. Also run `SELECT count(*) AS unsanitized_members FROM public.member WHERE telegram_username IS NOT NULL OR display_name NOT LIKE 'Member %' OR telegram_user_id < 8000000000000000;` and confirm zero. The transaction script validates the other sanitized columns. Report counts and pass/fail only, never raw names, IDs, free text or credentials. The script's own validation compares pre/post values during the transaction; the separate post-commit check confirms the committed copy.

### Separate development bot and H2 smoke check (human only)

1. In Telegram, ask `@BotFather` for `/newbot`; choose a **new** username ending in `bot`. Keep its token private. Add only this new bot to a disposable test group; do not add the production bot. Ensure no other process polls the new token. The current code registers its command menu through `SetMyCommands` at startup.
2. In a **new** PowerShell window from the `dev` checkout, confirm `git branch --show-current` prints `dev`. Use a fresh H2 path outside the repository; do not import the Neon dump into H2. Paste the following locally. The token prompt does not echo characters. Use the new bot's username without `@`.

   ```powershell
   if ((git branch --show-current).Trim() -ne 'dev') { throw 'Stop: checkout dev first.' }
   $secureToken = Read-Host 'New development bot token' -AsSecureString
   $tokenPtr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secureToken)
   try { $env:BOT_TOKEN = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($tokenPtr) }
   finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($tokenPtr); $secureToken.Dispose() }
   $env:BOT_USERNAME = Read-Host 'New bot username without @'
   $env:SPRING_PROFILES_ACTIVE = 'dev'
   $devDir = Join-Path $env:LOCALAPPDATA 'OrtaqDevBot'
   New-Item -ItemType Directory -Force -Path $devDir | Out-Null
   $devDb = (Join-Path $devDir 'homebot').Replace('\','/')
   $env:SPRING_DATASOURCE_URL = "jdbc:h2:file:$devDb;AUTO_SERVER=TRUE;DB_CLOSE_DELAY=-1"
   .\mvnw.cmd spring-boot:run
   ```

3. Wait for Spring startup and `Telegram command menu registered successfully`. If another local service uses port 8080, set `$env:PORT='8081'` **before** starting. If Telegram reports a 409 polling conflict, stop the other instance using the new token. Do not use the production token or production group.
4. In private chat with the **new** bot, send `/start` and `/komek`; expect a greeting and command help. In the disposable group, send `/start@YourNewBotUsername`, `/komek@YourNewBotUsername`, `/balans@YourNewBotUsername`, `/kezek@YourNewBotUsername`, and `/tizim@YourNewBotUsername` (replace the placeholder with the actual username). Confirm each returns a response and no generic error. A fresh H2 database may have no expenses, cooking schedule, or shopping items; those should show their normal empty states. Have a second consenting test account send `/start` in the group if you want to inspect a two-member balance list. Tap `/` in the bot's private chat and in the group and confirm the command menu appears with the legacy commands, including these five. Record pass/fail and any error text without token or personal data.
5. Stop with `Ctrl+C`. In that same window run `Remove-Item Env:BOT_TOKEN,Env:BOT_USERNAME,Env:SPRING_DATASOURCE_URL,Env:SPRING_PROFILES_ACTIVE -ErrorAction SilentlyContinue`. The H2 files stay in `%LOCALAPPDATA%\OrtaqDevBot`, separate from the repository's `data/homebot` and from Neon. Do not report the Telegram check as passed until actually performed.

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

WITH paid AS (
  SELECT household_id, payer_id AS member_id, sum(amount) AS total
  FROM expense WHERE NOT deleted GROUP BY household_id, payer_id
), owed AS (
  SELECT e.household_id, s.member_id, sum(s.share_amount) AS total
  FROM expense e JOIN expense_share s ON s.expense_id = e.id
  WHERE NOT e.deleted GROUP BY e.household_id, s.member_id
)
SELECT m.id AS member_id, coalesce(p.total, 0) - coalesce(o.total, 0) AS balance
FROM member m
LEFT JOIN paid p ON p.household_id = m.household_id AND p.member_id = m.id
LEFT JOIN owed o ON o.household_id = m.household_id AND o.member_id = m.id
ORDER BY m.id;

SELECT count(*) AS cross_household_payers
FROM expense e JOIN member m ON m.id = e.payer_id
WHERE e.household_id <> m.household_id;
SELECT count(*) AS cross_household_shares
FROM expense_share s JOIN expense e ON e.id = s.expense_id
JOIN member m ON m.id = s.member_id
WHERE e.household_id <> m.household_id;
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

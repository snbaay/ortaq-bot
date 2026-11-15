# Ortaq 2.0 safe release guide

This is a checklist for a later implementation release. **No production migration, deployment or Telegram test was done during planning.** Production data is on the existing Neon PostgreSQL instance. The owner performs credential, backup, BotFather and production approval steps; never put secrets in Git or test output.

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
- [ ] Owner confirms provenance and public-repository permission for the local untracked `docs/img/aktos-reference.jpg`, visually approves assets, uploads the sticker pack and configures IDs. Core bot launch may proceed without sticker assets only if V2-07 is recorded as a named release exception with owner and due date; no sticker task is falsely checked.
- [ ] Owner reviews Kazakh copy and redacted screenshots, updates README live URL when known, and checks Neon/Render current plan limits and backup retention. Existing `docs/DEPLOY.md` currently contains obsolete instructions to use bare numeric group messages and disable privacy for that purpose; V2-08-04 must replace those statements before launch.

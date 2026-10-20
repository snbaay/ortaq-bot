\# Ortaq — Project Plan (for the coding agent)



Ortaq (ортақ = "shared") is a Telegram bot for roommates. One Telegram group = one household. The bot tracks shared expenses and debts, runs a cooking rota, and has small helpers for shared life (shopping list, notes, chore log, payment deadlines, randomizer). The bot UI is in \*\*Kazakh\*\*.



\---



\## 0. How to work (READ FIRST)



\- Read this whole file and `AGENTS.md` (if present) before coding. If they conflict: this file wins on SCOPE, the stricter rule wins on SAFETY (secrets, git).

\- Work on the FIRST unchecked task `\[ ]` in section 7. One task at a time, in order. Do not skip, do not jump ahead, do not add features that are not in this file.

\- For every task:

&#x20; 1. Read only the existing code the task touches. Do NOT rewrite files that already work.

&#x20; 2. Write unit tests FIRST for pure classes.

&#x20; 3. Implement.

&#x20; 4. Run `.\\mvnw.cmd -q test 2>\&1 | Select-Object -Last 40`. Max 3 fix-and-rerun cycles. If still failing: write the problem under "## 10. Blockers" and STOP.

&#x20; 5. Run `git status`. If it shows `.env`, `target/`, `data/`, `\*.db`, `\*.log` or anything that looks like a secret: write it under Blockers and STOP. Do not commit.

&#x20; 6. Change `\[ ]` to `\[x]` for the task in this file, then `git add .` and `git commit -m "<message from the task>"`.

\- After the LAST task of a phase: `git push` (branch `dev`, never `main`).

\- SAVE QUOTA:

&#x20; - Reply after each task in max 8 lines: changed files, how to check it in Telegram. No long explanations.

&#x20; - Never print or view full logs or whole files. Never re-read a file you just wrote. Prefer small edits to rewrites.

&#x20; - If quota ends, nothing is lost: progress is in git and in the checkboxes here. Resume prompt: `Read plan.md and continue with the first unchecked task.`

\- Never ask for the real bot token. Never print `BOT\_TOKEN`. Tests use fake values: `BOT\_TOKEN=123456:fake\_token`, `BOT\_USERNAME=test\_bot`.

\- Do NOT run the bot (`spring-boot:run`): the process never ends and blocks the terminal. The human runs it.



\---



\## 1. Fixed facts



\- Bot: `@OrtaqUiBot`, name "Ortaq". Long polling.

\- UI language: Kazakh only. Tone: informal ("сен", not "сіз"), short, 0–2 emojis per message.

\- Stack: Java 21 (JDK 22 is installed locally, compile with `release 21`), Spring Boot 3.3.4, Maven Wrapper, TelegramBots 7.11.0 (`telegrambots-springboot-longpolling-starter`), Spring Data JPA, H2 file DB (profile `dev`), PostgreSQL (profile `prod`), JUnit 5. \*\*No Lombok.\*\*

\- Package root `kz.homebot`: `bot`, `service`, `domain`, `repository`, `config` (add sub-packages only when needed).

\- Env vars: `BOT\_TOKEN`, `BOT\_USERNAME`; prod only: `SPRING\_DATASOURCE\_URL`, `SPRING\_DATASOURCE\_USERNAME`, `SPRING\_DATASOURCE\_PASSWORD`, `PORT`; optional: `ALLOWED\_CHAT\_IDS`.

\- Machine: Windows + PowerShell, 4 GB RAM. No Docker locally, no heavy local services.

\- Time zone: always explicit `Asia/Almaty`. Never use the server default zone.

\- Money: tenge (₸), `BigDecimal` scale 2, DB column `NUMERIC(12,2)`, max amount 100 000 000. Never `double`/`float`.

\- Git: branch `dev`. The GitHub repo `snbaay/ortaq-bot` is \*\*PUBLIC\*\* — never commit secrets.



\---



\## 2. Code rules



\- Junior-friendly code: small classes, constructor injection, Java `record` for simple data, no interface with a single implementation, no over-engineering.

\- \*\*Pure classes\*\* (no Spring, no JPA, no I/O, easy to unit test): `ExpenseSplitter`, `ExpenseParser`, `DebtCalculator`, `MoneyFormatter`, `CommandParser`, `NextCookPicker`, `DigestPolicy`, `PaymentCountdown`, `RandomPicker`. Keep them annotation-free and expose them as beans in one `@Configuration` class (`LogicConfig`).

\- All Kazakh text lives ONLY in `src/main/resources/messages\_kk.properties` (UTF-8), read through `MessageService`. No Kazakh string literals in Java. Key style: `area.action.result`, e.g. `expense.added`.

\- Bot structure: `HomeTelegramBot` only receives updates and routes them. Each area has its own handler class implementing `CommandHandler` (`List<String> commands()`, `void handle(CommandContext ctx)`). A `CommandRouter` finds the handler by command name. `CommandContext` holds: chat id, chat type, sender (telegram id, name, username), args text, message entities.

\- Telegram command names are lowercase ASCII letters/digits/underscore, max 32 chars. Menu descriptions are Kazakh. In groups commands can arrive as `/cmd@OrtaqUiBot args` — the parser must handle the suffix for ALL commands (and ignore commands addressed to other bots).

\- Plain text messages (no Markdown/HTML parse mode) to avoid escaping bugs.

\- Telegram message limit is 4096 chars: truncate long lists and end with "…".

\- Group-only commands reply with a short Kazakh "works only in the group" message in a private chat. `/start` and `/komek` work everywhere.

\- Errors: the router catches every exception from a handler, logs it with context (no secrets), and replies with the generic error message. The bot must never stop polling because of one bad message.

\- Logging: INFO for business events (member registered, expense added with id). Never log `BOT\_TOKEN` or full message texts.

\- Database: explicit table names, `@Transactional` on services that write, indexes for lookups by household. `ddl-auto=update` for dev and prod (documented limitation; Flyway is in the backlog).

\- Tests: JUnit 5 for pure classes; `@DataJpaTest`/`@SpringBootTest` on H2 for DB services; no real Telegram calls anywhere in tests.



\---



\## 3. Commands (final list)



| Command | Menu description (Kazakh) | Behavior |

|---|---|---|

| `/start` | Бастау | Greeting + short "how it works". Works everywhere. |

| `/komek` | Көмек | Help: all commands with examples. Works everywhere. |

| `/shygyn` | Шығын қосу | `/shygyn <amount> <description> \[@user ...]` add an expense. |

| `/balans` | Баланс | Net balance of every member. |

| `/qaryz` | Кім кімге қарыз | Minimal list of transfers to settle everything. |

| `/tolem` | Қарызды жабу | `/tolem <name> <amount>` record a repayment. |

| `/oshiru` | Жазбаны өшіру | Delete your last entry, or `/oshiru <id>` your entry by id. |

| `/tarih` | Соңғы шығындар | Last 10 entries with ids. |

| `/esep` | Айлық есеп | Monthly report. |

| `/kezek` | Бүгін кім тамақ жасайды | Today's cook + next 3 days. |

| `/otkizu` | Кезекті өткізу | Skip today's turn. |

| `/dayyn` | Ас дайын | "Food is ready" announcement to the group. |

| `/tizim` | Сатып алу тізімі | No args: show list. With text: add item(s), comma-separated. |

| `/satyp` | Сатып алынды | `/satyp <id>` mark item as bought. |

| `/jazba` | Ортақ жазбалар | No args: last 10 notes. With text: add note. |

| `/jazba\_oshiru` | Жазбаны жою | `/jazba\_oshiru <id>` delete a note. |

| `/is` | Атқарылған іс | `/is <text>` log a chore ("changed the water filter"). |

| `/ister` | Істер тізімі | Last 10 chores. |

| `/songy` | Соңғы рет қашан | `/songy <text>` when was this chore last done. |

| `/merzim` | Төлем мерзімдері | List monthly payments with days-left countdown. |

| `/merzim\_qosu` | Мерзім қосу | `/merzim\_qosu <day 1-31> <amount or -> <title>` e.g. `/merzim\_qosu 5 150000 Жалдау`. |

| `/merzim\_oshiru` | Мерзімді жою | `/merzim\_oshiru <id>` delete a payment deadline. |

| `/kim` | Кездейсоқ адам | Random member; optional text: `/kim who takes out the trash`. |

| `/tandau` | Кездейсоқ таңдау | `/tandau a, b, c` (2–10 options, comma or `\\|`). |

| `/tiyn` | Тиын лақтыру | Coin flip: "Елтаңба" or "Сан". |



Sample messages (write the rest in the same style):

\- `expense.added`: `✅ Шығын қосылды: {description} — {amount}` / `Төлеген: {payer}` / `Әрқайсысына: {share}`

\- `error.generic`: `Бір нәрсе дұрыс болмады, кейінірек қайталап көр 🙏`

\- `error.groupOnly`: `Бұл команда тек топта жұмыс істейді.`

\- `debts.allEven`: `Қарыз жоқ, бәрі тең 🎉`

\- `usage.shygyn`: `Былай жаз: /shygyn 630 су`



\---



\## 4. Domain model



\- \*\*Household\*\*: id, telegramChatId (unique), title, createdAt, lastDigestDate (LocalDate, nullable).

\- \*\*Member\*\*: id, household, telegramUserId, displayName, telegramUsername (nullable), createdAt. Unique (household, telegramUserId). Display name = first name + last name if present. Username and name are refreshed on every message.

\- \*\*Expense\*\*: id, household, payer (Member), amount, description, createdAt, deleted (boolean, soft delete), settlement (boolean; true = repayment).

\- \*\*ExpenseShare\*\*: id, expense, member, shareAmount.

\- \*\*CookingDuty\*\*: id, household, member, dutyDate (LocalDate). Unique (household, dutyDate).

\- \*\*ShoppingItem\*\*: id, household, text, addedBy, addedAt, bought, boughtBy (nullable), boughtAt (nullable).

\- \*\*Note\*\*: id, household, author, text, createdAt, deleted.

\- \*\*ChoreLog\*\*: id, household, member, text, createdAt.

\- \*\*PaymentDeadline\*\*: id, household, title, dayOfMonth (1–31), amount (nullable), createdBy, active.



Balance rule: for every NOT deleted expense the payer gets `+amount` and every share member gets `-shareAmount`. A repayment (`settlement = true`) is an expense with the payer = who paid back and ONE share = recipient with the full amount. Balances of a household always sum to 0.



\---



\## 5. Current state (at the time of writing)



\- DONE and pushed: skeleton (Maven, Spring Boot, long polling bot, `MessageService` + `messages\_kk.properties`, `/health`, dev profile) and entities `Household`, `Member`, `Expense`, `ExpenseShare` with repositories and `MemberRegistrationService` (registration on every group message).

\- DRAFT, uncommitted, never built: a previous session was cut off by a quota limit while writing `ExpenseSplitter`, `ExpenseParser`, `MoneyFormatter`, `DebtCalculator` (+ tests), `ExpenseService`, `BalanceService`, repository queries, extra messages and a rewritten `HomeTelegramBot`. It was in the middle of registering pure classes as Spring beans. Treat all of it as a draft: reuse what is good, fix what is broken.



\---



\## 6. Definition of done (every task)



Compiles, quiet test run passes, no Kazakh literals in Java, `git status` is clean of forbidden files, task is checked off and committed.



\---



\## 7. Task list



\### Phase A — Money core



\- \[x] \*\*A0. Audit and stabilize the draft.\*\* Read the existing source and tests. In max 10 lines list what is missing or broken compared to this plan. Make the project compile and tests pass. Remove `@Component` from pure classes (use `LogicConfig`). Do NOT add new features. Commit: `chore: stabilize draft steps 3-4`.

\- \[x] \*\*A1. Bot infrastructure.\*\* `CommandParser` (pure, tests first: `/cmd`, `/cmd@OrtaqUiBot args`, `/cmd@OtherBot` is ignored, case-insensitive, extra spaces, no args, plain text is not a command). `CommandHandler`, `CommandContext`, `CommandRouter`, generic error handling, menu registration (`SetMyCommands`) built from all handlers' commands with Kazakh descriptions from `messages\_kk.properties`, `/start` and `/komek`. Move existing command code from `HomeTelegramBot` into handlers. Commit: `feat: command router and help`.

\- \[ ] \*\*A2. Expenses.\*\* Pure `ExpenseParser`, `ExpenseSplitter`, `MoneyFormatter` (tests first), `ExpenseService`, `ExpenseHandler`.

&#x20; - `/shygyn 630 су` in a group creates an expense paid by the sender. A plain group message that starts with a number (`630 су`, `2 500,50 хлеб`) does the same; other plain messages are ignored.

&#x20; - Amount: positive, max 2 decimals, accepts `.` or `,` as separator (not both) and spaces inside the number. Reject 0, negative, 3+ decimals, above the max. Description required, max 100 chars. Wrong format → short Kazakh error with an example.

&#x20; - Default split: equally among ALL current household members. Shares must sum to the amount exactly: leftover tiyn are distributed one by one (100.00 among 3 = 33.34, 33.33, 33.33).

&#x20; - Reply in Kazakh: amount, description, payer, each person's share.

&#x20; - Parser tests: valid, comma decimal, spaces, zero, negative, 3 decimals, both separators, no description, too long description. Splitter tests: even split, 100.00/3, 0.01/3, 1 person, 2 people with odd tiyn. `MoneyFormatter`: space as thousands separator, comma as decimal separator, drop `,00`, add ` ₸`.

&#x20; - Commit: `feat: expenses with equal split`.

\- \[ ] \*\*A3. Balances and debts.\*\* Pure `DebtCalculator`, `BalanceService`, `BalanceHandler`.

&#x20; - `DebtCalculator`: `Map<Long, BigDecimal>` balances → `List<Transfer>` (`record Transfer(long fromMemberId, long toMemberId, BigDecimal amount)`). Greedy: repeatedly the biggest debtor pays the biggest creditor `min(debt, credit)` until all are zero. Ties broken by smaller member id (deterministic). Ignore zero balances.

&#x20; - Tests first, at least 8: two people; three people (one pays for all); all zero; empty map; 0.01 among 3; chain A owes B and B owes C is simplified; two creditors and one debtor; deterministic tie-break. Also assert: total of transfers equals total of positive balances.

&#x20; - `BalanceService` uses JPQL aggregation (SUM paid per payer, SUM shares per member, `deleted = false`). Do not load all expenses into memory.

&#x20; - `/balans`: each member's balance, sorted from biggest to smallest (`Асан: +1 200 ₸`, `Берік: -600 ₸`). `/qaryz`: transfers (`Берік → Асан: 600 ₸`) or `debts.allEven`.

&#x20; - In the final reply to the human, explain the `DebtCalculator` algorithm in plain words with a numeric example.

&#x20; - Commit: `feat: balances and debt calculator`.

\- \[ ] \*\*A4. Repayments, history, undo.\*\* `/tolem`, `/tarih`, `/oshiru`.

&#x20; - `/tolem <name> <amount>`: the sender paid back money. Find the recipient by display name (case-insensitive prefix match). If the household has exactly 2 members the name may be omitted. Not found or ambiguous → reply with the member names. Reject: recipient = sender, amount ≤ 0, more than 2 decimals. Store as a settlement expense (section 4). Reply `Берік Асанға 600 ₸ төледі ✅` and then show the updated `/qaryz`.

&#x20; - `/tarih`: last 10 not-deleted entries (expenses and repayments) with `#id`, date, who, amount, description.

&#x20; - `/oshiru`: no args → soft-delete the sender's last not-deleted entry; `/oshiru <id>` → that entry, only if the sender is its payer. Nothing to delete → short message.

&#x20; - Service-level tests: full settlement → no debts; partial settlement reduces the debt; undo restores balances; a user cannot delete someone else's entry.

&#x20; - Commit: `feat: repayments, history and undo`.

\- \[ ] \*\*A5. Split among selected people.\*\* Add `telegramUsername` handling (refresh on every message). `/shygyn 3000 пицца @berik @aknyet` splits the expense among the mentioned members AND the payer (the payer is always included; say so in `/komek`). Use Telegram message entities (`mention`, `text\_mention`) to read mentions. Unknown `@username` → error listing the known usernames. Without mentions the split is among all members. Tests for the mention-based split. Commit: `feat: split among mentioned members`.



\*\*After A5: `git push`.\*\*



\### Phase B — Household life



\- \[ ] \*\*B1. Cooking rota.\*\* Pure `NextCookPicker` (tests first, ≥5: first day, normal step, wrap-around, member list grew, previous cook no longer in the list), `CookingRotationService`, `CookingHandler`.

&#x20; - Members cook one per day in registration order (by member id).

&#x20; - `getOrCreateDuty(household, date)`: if no row for the date, assign the member after the previous assigned cook (cyclic); the first day starts with the first member.

&#x20; - `/kezek`: who cooks today + next 3 days (compute upcoming days without saving them). `/otkizu`: the next person in order cooks today and the rotation continues from there. `/dayyn`: posts `🍲 {name} тамақты дайындады, ас болсын!` to the group.

&#x20; - Fewer than 2 members → Kazakh message that at least 2 people are needed. Use `LocalDate.now(ZoneId.of("Asia/Almaty"))`.

&#x20; - Commit: `feat: cooking duty rota`.

\- \[ ] \*\*B2. Daily digest.\*\* Pure `DigestPolicy.shouldSend(LocalDateTime nowAlmaty, LocalDate lastDigestDate)` → true if local time ≥ 09:00 and `lastDigestDate` is not today (tests first: before 09:00, at 09:00, after 09:00, already sent today, null last date, after midnight). A `@Scheduled(fixedRate = 15 min)` job (`@EnableScheduling`) checks every household and sends ONE message: `☀️ Қайырлы таң! Бүгін тамақты {name} жасайды 🍲` (just the cook for now), then sets `lastDigestDate`. If a chat is unreachable, log a warning and continue with the others. This design survives restarts and short downtime. Commit: `feat: daily digest`.

\- \[ ] \*\*B3. Shopping list.\*\* `ShoppingItem`, `ShoppingService`, `ShoppingHandler`. `/tizim` shows open items with `#id`. `/tizim сүт, нан, жұмыртқа` adds 3 items. `/satyp 12` marks #12 as bought (hidden from the list afterwards, kept in the DB; only items of the same household). Limits: item text max 60 chars, max 50 open items. Tests for add/bought/limits. Commit: `feat: shopping list`.

\- \[ ] \*\*B4. Shared notes.\*\* `/jazba` shows the last 10 notes (`#id text — author`). `/jazba <text>` adds a note (max 300 chars). `/jazba\_oshiru <id>` soft-deletes a note of the same household. Tests. Commit: `feat: shared notes`.

\- \[ ] \*\*B5. Chore log.\*\* `/is <text>` logs a chore for the sender (max 100 chars). `/ister` shows the last 10 (`date — name: text`). `/songy <text>` finds the most recent entry containing the text (case-insensitive) and answers like `Соңғы рет: 12 қазан, Асан — су сүзгісі (5 күн бұрын)`; no match → short message. Tests for the lookup and for "days ago" (pure function). Commit: `feat: chore log`.

\- \[ ] \*\*B6. Payment deadlines with countdown.\*\* Pure `PaymentCountdown.daysLeft(LocalDate today, int dayOfMonth)`: days until the next occurrence; if the month is shorter than `dayOfMonth` use the last day of that month (tests first: same day = 0, tomorrow, next month, 31 in a 30-day month, 31 in February, 29 February in a leap year and not in a leap year). `PaymentDeadline` entity, service, `DeadlineHandler`: `/merzim\_qosu 5 150000 Жалдау` (amount `-` means none; title max 40 chars; max 20 deadlines per household), `/merzim` lists all sorted by days left (`Жалдау — 150 000 ₸ — 5 күн қалды`, `бүгін`, `ертең`), `/merzim\_oshiru <id>`. Extend the daily digest (B2) with reminder lines `⏰ {title}: {n} күн қалды` when days left is 3, 1 or 0. Tests. Commit: `feat: payment deadlines with countdown`.

\- \[ ] \*\*B7. Randomizer.\*\* Pure `RandomPicker` that takes a `java.util.random.RandomGenerator` (tests with a seeded generator). `/kim` picks a random household member (`🎲 {name}`, optional text before the answer). `/tandau a, b, c` picks one option (2–10 options, split by comma or `|`, trim, ignore empty). `/tiyn` returns "Елтаңба" or "Сан". Tests. Commit: `feat: randomizer`.



\*\*After B7: `git push`.\*\*



\### Phase C — Reports



\- \[ ] \*\*C1. Monthly report.\*\* `/esep` = current month (Asia/Almaty), `/esep 9` = month number 1–12 of the current year. Not-deleted expenses only, repayments excluded. Show: total household spending, per member "paid" and "consumed" (shares), top 3 biggest expenses. Use aggregation queries, not in-memory loops over all rows. Tests for the month boundaries (first and last day of the month, time zone). Commit: `feat: monthly report`.



\### Phase D — Quality and safety



\- \[ ] \*\*D1. Allowed chats and input limits.\*\* Optional env var `ALLOWED\_CHAT\_IDS` (comma-separated). If set, updates from other chats are ignored (log at DEBUG). Centralize all limits (text lengths, item counts, amount max) in one `Limits` class. Tests. Commit: `feat: allowed chats and limits`.

\- \[ ] \*\*D2. Full-flow test.\*\* One `@SpringBootTest` scenario on H2 using services directly (no Telegram): register 3 members; add expenses (equal split and mention split); check balances and `/qaryz` transfers; repay; undo; check balances again; cooking rota across 5 days; deadline countdown. Commit: `test: full flow scenario`.

\- \[ ] \*\*D3. Cleanup pass.\*\* Search Java files for Kazakh literals and move them to `messages\_kk.properties`; remove unused message keys and dead code; make sure every command in section 3 is registered in the menu and described in `/komek`; check logs do not contain secrets. Commit: `chore: cleanup`.



\*\*After D3: `git push`.\*\*



\### Phase E — Deployment preparation



\- \[ ] \*\*E1. Production setup.\*\* Profile `prod` reads the DB from `SPRING\_DATASOURCE\_\*` env vars and the port from `${PORT:8080}`; Hikari `maximum-pool-size: 3`. `Dockerfile` multi-stage: build stage `maven:3.9-eclipse-temurin-21` running `mvn -q -DskipTests package`; run stage `eclipse-temurin:21-jre` with `ENV SPRING\_PROFILES\_ACTIVE=prod`, `EXPOSE 8080` and `ENTRYPOINT \["java","-XX:MaxRAMPercentage=70","-jar","app.jar"]`. Add `.dockerignore` (`target`, `data`, `.git`, `.env`, `\*.db`). Do NOT run Docker (it is not installed locally). Commit: `build: docker and prod profile`.

\- \[ ] \*\*E2. CI.\*\* `.github/workflows/ci.yml`: on push and pull\_request, `ubuntu-latest`, `actions/checkout@v4`, `actions/setup-java@v4` (Temurin 21), `mvn -B -q test` with fake `BOT\_TOKEN` and `BOT\_USERNAME`. Commit: `ci: run tests on GitHub Actions`.

\- \[ ] \*\*E3. Deployment guide.\*\* `docs/DEPLOY.md` with exact steps for a human: create a free Neon Postgres and copy the JDBC URL (`jdbc:postgresql://HOST/DB?sslmode=require`); create a Render Web Service from the GitHub repo using the Dockerfile; env vars to set; why an external pinger is needed (free Render web services sleep after about 15 minutes without incoming HTTP traffic, and long polling is outgoing traffic, so it does not keep the service awake); set up UptimeRobot or cron-job.org to call `https://<service>.onrender.com/health` every 5 minutes; use a SEPARATE second bot (created in BotFather) for local development, otherwise two instances polling with the same token cause Telegram error 409; verify current free-tier limits on the providers' sites. Commit: `docs: deployment guide`.



\*\*After E3: `git push`.\*\*



\### Phase F — Portfolio



\- \[ ] \*\*F1. README.md (English).\*\* What the project is (one paragraph), features with the command table, a Mermaid architecture diagram (Telegram → long polling → CommandRouter → handlers → services → repositories → DB; scheduler → daily digest), tech stack, how to run locally on Windows (env vars, `.\\mvnw.cmd spring-boot:run`), env var table, deployment link, the `DebtCalculator` explanation with a numeric example, key design decisions (BigDecimal, pure logic classes, soft delete, time zone, digest idempotency), known limitations, roadmap from the backlog. Add placeholders `docs/img/` for screenshots/GIFs. Commit: `docs: readme`.

\- \[ ] \*\*F2. docs/INTERVIEW.md (simple English).\*\* 12 likely interview questions with short answers from this project: why long polling and not webhook; why `BigDecimal`; how the greedy debt simplification works, its complexity and limits; why soft delete; why pure classes; how time zones are handled; how duplicate digests are avoided; how errors are handled; how the DB is accessed efficiently; what would change for multi-instance deployment; testing strategy; what I would improve. Commit: `docs: interview notes`.



\*\*After F2: `git push`. The plan is complete.\*\*



\---



\## 8. Backlog (do NOT build unless the human asks)



Telegram Mini App dashboard with `initData` validation; Flyway migrations; webhook mode; handling members who leave the group (`chat\_member` updates); recurring expenses created automatically; CSV export; receipt photo OCR; Russian/Kazakh language switch; rate limiting; metrics.



\---



\## 9. Human tasks (the agent cannot do these)



1\. In BotFather: `/setprivacy` → `@OrtaqUiBot` → Disable (so the bot sees plain messages like `630 су`). Add the bot to the group; every roommate writes one message so they get registered.

2\. Set `BOT\_TOKEN` and `BOT\_USERNAME` in your own terminal and run the bot yourself. If a token was ever shared anywhere, run `/revoke` in BotFather first.

3\. Create a second bot for local development (so it does not clash with the deployed one).

4\. Read `messages\_kk.properties` and fix the Kazakh wording.

5\. Create the Neon, Render and UptimeRobot accounts (see `docs/DEPLOY.md`).

6\. Take screenshots/GIFs for the README into `docs/img/`.



\---



\## 10. Blockers



(The agent writes here when it has to stop. Empty = none.)


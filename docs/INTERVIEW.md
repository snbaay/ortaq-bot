# Ortaq interview notes

1. **Why long polling instead of a webhook?**  
   It needs no public Telegram callback URL or webhook setup. The bot asks Telegram for updates. This is simple for a single small instance, but it needs a running process.

2. **Why use `BigDecimal` for money?**  
   Binary floating point cannot represent many decimal amounts exactly. `BigDecimal` keeps tenge and tiyn precise, and the splitter distributes leftover tiyn explicitly.

3. **How does debt simplification work? What are its limits?**  
   Repeatedly pair the largest debtor with the largest creditor and transfer the smaller balance. For `A +900`, `B -600`, `C -300`, the transfers are `B → A 600` and `C → A 300`. The implementation scans members for every transfer, so it is roughly O(n²). Greedy settlement clears all balances, but it does not guarantee the mathematically fewest transfers in every case.

4. **Why soft-delete expenses and notes?**  
   Undo removes them from balances and lists while keeping the original rows for audit and possible recovery. A hard delete would erase that history.

5. **Why put parsing and calculation in pure classes?**  
   They can be tested quickly without Spring, a database, or Telegram. Services then connect those rules to persistence.

6. **How are time zones handled?**  
   Calendar decisions use `Asia/Almaty` explicitly. Monthly reports query timestamps from the first instant of the chosen local month to the first instant of the next month.

7. **How is a duplicate morning digest avoided?**  
   Each household stores `lastDigestDate`. The scheduler sends only after 09:00 local time and only when that date differs from today. It writes the date after a successful send. It reads that weekday's cooks from a repeating schedule, applies swaps for today, and skips cooking text on days off. Multiple concurrent instances would need a database lock or atomic claim.

8. **What happens when a command fails?**  
   `CommandRouter` catches handler exceptions, logs the command and chat context, and replies with a generic Kazakh error. One bad update does not stop polling.

9. **How is database access kept efficient?**  
   Balance and monthly report totals use SQL aggregation through JPQL. History and top expenses have bounded queries; the bot does not load every expense to compute a report.

10. **What would change for multiple instances?**  
    Long polling with one token must have one active poller. A webhook with an HTTP load balancer would fit multiple instances better. Scheduler work would also need locking or a queue to prevent duplicate digests.

11. **What is the testing strategy?**  
    Unit tests cover pure parsing, splitting, debt, date, and random logic. H2 tests cover repository services. A full Spring Boot test runs a three-member flow without Telegram network calls.

12. **What would you improve next?**  
    Add Flyway migrations in place of `ddl-auto=update`, handle members leaving a group, add idempotency and locking for multi-instance deployment, and monitor failures and database usage.

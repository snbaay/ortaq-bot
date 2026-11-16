-- Run ONLY with credentials for the separate Neon project ortaq-restore-check.
-- Review the connected project/host outside SQL: a database name alone cannot prove project identity.
-- Use psql -X -v ON_ERROR_STOP=1 -W -f on the isolated database.
-- Keep the original dump outside Git. A failed statement aborts this transaction.
BEGIN;

DO $guard$
BEGIN
    IF current_database() <> 'ortaq_restore_check' THEN
        RAISE EXCEPTION 'Refusing sanitization: wrong database name';
    END IF;
    IF EXISTS (
        SELECT 1 FROM (VALUES
            ('household'), ('member'), ('expense'), ('expense_share'),
            ('cooking_schedule'), ('cooking_swap'), ('shopping_item'),
            ('note'), ('chore_log'), ('payment_deadline')
        ) AS required(table_name)
        WHERE to_regclass('public.' || required.table_name) IS NULL
    ) THEN
        RAISE EXCEPTION 'Refusing sanitization: legacy schema is incomplete';
    END IF;
    -- The pseudonyms must not overlap existing or previously anonymized Telegram IDs.
    IF EXISTS (SELECT 1 FROM public.household
               WHERE id <= 0 OR id >= 1000000000000
                  OR telegram_chat_id <= -8000000000000000)
       OR EXISTS (SELECT 1 FROM public.member
                  WHERE id <= 0 OR id >= 1000000000000
                     OR telegram_user_id >= 8000000000000000) THEN
        RAISE EXCEPTION 'Refusing sanitization: ID range is unexpected or copy was already sanitized';
    END IF;
END
$guard$;

-- The staging copy must be idle. Hold write locks until commit so snapshots stay comparable.
LOCK TABLE public.household, public.member, public.expense, public.expense_share,
           public.cooking_schedule, public.cooking_swap, public.shopping_item,
           public.note, public.chore_log, public.payment_deadline
IN SHARE ROW EXCLUSIVE MODE;

CREATE TEMP VIEW _ortaq_counts AS
SELECT 'household' AS table_name, count(*) AS row_count FROM public.household
UNION ALL SELECT 'member', count(*) FROM public.member
UNION ALL SELECT 'expense', count(*) FROM public.expense
UNION ALL SELECT 'expense_share', count(*) FROM public.expense_share
UNION ALL SELECT 'cooking_schedule', count(*) FROM public.cooking_schedule
UNION ALL SELECT 'cooking_swap', count(*) FROM public.cooking_swap
UNION ALL SELECT 'shopping_item', count(*) FROM public.shopping_item
UNION ALL SELECT 'note', count(*) FROM public.note
UNION ALL SELECT 'chore_log', count(*) FROM public.chore_log
UNION ALL SELECT 'payment_deadline', count(*) FROM public.payment_deadline;

CREATE TEMP VIEW _ortaq_balances AS
WITH paid AS (
    SELECT household_id, payer_id AS member_id, sum(amount) AS total
    FROM public.expense WHERE NOT deleted GROUP BY household_id, payer_id
), owed AS (
    SELECT e.household_id, s.member_id, sum(s.share_amount) AS total
    FROM public.expense e JOIN public.expense_share s ON s.expense_id = e.id
    WHERE NOT e.deleted GROUP BY e.household_id, s.member_id
)
SELECT m.id AS member_id, m.household_id,
       coalesce(p.total, 0) - coalesce(o.total, 0) AS balance
FROM public.member m
LEFT JOIN paid p ON p.household_id = m.household_id AND p.member_id = m.id
LEFT JOIN owed o ON o.household_id = m.household_id AND o.member_id = m.id;

CREATE TEMP TABLE _ortaq_before_counts ON COMMIT DROP AS SELECT * FROM _ortaq_counts;
CREATE TEMP TABLE _ortaq_before_balances ON COMMIT DROP AS SELECT * FROM _ortaq_balances;
CREATE TEMP TABLE _ortaq_before_expense ON COMMIT DROP AS
SELECT id, household_id, payer_id, amount, created_at, deleted, settlement FROM public.expense;
CREATE TEMP TABLE _ortaq_before_shares ON COMMIT DROP AS
SELECT id, expense_id, member_id, share_amount FROM public.expense_share;

-- Only direct identifiers and personal free text change. All internal keys and money remain intact.
UPDATE public.household
SET telegram_chat_id = -8000000000000000 - id,
    title = 'Household ' || id::text;
UPDATE public.member
SET telegram_user_id = 8000000000000000 + id,
    display_name = 'Member ' || id::text,
    telegram_username = NULL;
UPDATE public.expense SET description = '[redacted]';
UPDATE public.shopping_item SET text = '[redacted]';
UPDATE public.note SET text = '[redacted]';
UPDATE public.chore_log SET text = '[redacted]';
UPDATE public.payment_deadline SET title = '[redacted]';

DO $validate$
BEGIN
    IF EXISTS (SELECT * FROM _ortaq_before_counts EXCEPT SELECT * FROM _ortaq_counts)
       OR EXISTS (SELECT * FROM _ortaq_counts EXCEPT SELECT * FROM _ortaq_before_counts) THEN
        RAISE EXCEPTION 'Validation failed: legacy row counts changed';
    END IF;
    IF EXISTS (SELECT * FROM _ortaq_before_expense EXCEPT
               SELECT id, household_id, payer_id, amount, created_at, deleted, settlement FROM public.expense)
       OR EXISTS (SELECT id, household_id, payer_id, amount, created_at, deleted, settlement FROM public.expense
                  EXCEPT SELECT * FROM _ortaq_before_expense) THEN
        RAISE EXCEPTION 'Validation failed: expense keys, links, amounts or flags changed';
    END IF;
    IF EXISTS (SELECT * FROM _ortaq_before_shares EXCEPT
               SELECT id, expense_id, member_id, share_amount FROM public.expense_share)
       OR EXISTS (SELECT id, expense_id, member_id, share_amount FROM public.expense_share
                  EXCEPT SELECT * FROM _ortaq_before_shares) THEN
        RAISE EXCEPTION 'Validation failed: expense share keys, links or amounts changed';
    END IF;
    IF EXISTS (SELECT * FROM _ortaq_before_balances EXCEPT SELECT * FROM _ortaq_balances)
       OR EXISTS (SELECT * FROM _ortaq_balances EXCEPT SELECT * FROM _ortaq_before_balances) THEN
        RAISE EXCEPTION 'Validation failed: member balances changed';
    END IF;
    IF EXISTS (SELECT 1 FROM public.household
               WHERE telegram_chat_id <> -8000000000000000 - id
                  OR title <> 'Household ' || id::text)
       OR EXISTS (SELECT 1 FROM public.member
                  WHERE telegram_user_id <> 8000000000000000 + id
                     OR display_name <> 'Member ' || id::text
                     OR telegram_username IS NOT NULL)
       OR EXISTS (SELECT 1 FROM public.expense WHERE description <> '[redacted]')
       OR EXISTS (SELECT 1 FROM public.shopping_item WHERE text <> '[redacted]')
       OR EXISTS (SELECT 1 FROM public.note WHERE text <> '[redacted]')
       OR EXISTS (SELECT 1 FROM public.chore_log WHERE text <> '[redacted]')
       OR EXISTS (SELECT 1 FROM public.payment_deadline WHERE title <> '[redacted]') THEN
        RAISE EXCEPTION 'Validation failed: personal fields remain in the target columns';
    END IF;
    IF EXISTS (
        SELECT e.id FROM public.expense e
        LEFT JOIN public.expense_share s ON s.expense_id = e.id
        WHERE NOT e.deleted GROUP BY e.id, e.amount
        HAVING e.amount <> coalesce(sum(s.share_amount), 0)
    ) THEN
        RAISE EXCEPTION 'Validation failed: expense and share amounts differ';
    END IF;
    IF EXISTS (SELECT household_id FROM _ortaq_balances
               GROUP BY household_id HAVING sum(balance) <> 0) THEN
        RAISE EXCEPTION 'Validation failed: household balance is nonzero';
    END IF;
END
$validate$;

-- The temp snapshots are dropped here. If validation raises, psql ON_ERROR_STOP exits
-- and the connection rollback leaves all persistent tables unchanged.
COMMIT;

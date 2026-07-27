-- Schema up to this point was created by hibernate ddl-auto=update. This is the
-- first managed migration; it adds what ddl-auto never created - the indexes the
-- hot queries need, and the uniqueness that makes duplicate token numbers
-- impossible rather than merely unlikely.

-- ---------------------------------------------------------------------------
-- tokens
-- ---------------------------------------------------------------------------

-- Business day the token belongs to. Backfilled from created_at for existing
-- rows so the unique constraint below can be applied.
ALTER TABLE tokens ADD COLUMN IF NOT EXISTS token_date date;
UPDATE tokens SET token_date = created_at::date WHERE token_date IS NULL;
ALTER TABLE tokens ALTER COLUMN token_date SET NOT NULL;

-- is_transfer was added by ddl-auto without a default, so rows created before
-- it exist with NULL. ReportService does .filter(Token::getIsTransfer), which
-- unboxes and throws NullPointerException on those - the transferred-tokens
-- report is broken for any branch with older data.
UPDATE tokens SET is_transfer = false WHERE is_transfer IS NULL;
ALTER TABLE tokens ALTER COLUMN is_transfer SET DEFAULT false;
ALTER TABLE tokens ALTER COLUMN is_transfer SET NOT NULL;

-- De-duplicate before adding the constraint. Any existing duplicates are given
-- fresh sequence numbers above the current maximum for that day, keeping the
-- earliest row on its original number.
WITH ranked AS (
    SELECT id,
           branch_id,
           priority,
           token_seq,
           token_date,
           ROW_NUMBER() OVER (PARTITION BY branch_id, priority, token_seq, token_date
                              ORDER BY created_at, id) AS rn
    FROM tokens
),
maxes AS (
    SELECT branch_id, priority, token_date, MAX(token_seq) AS max_seq
    FROM tokens
    GROUP BY branch_id, priority, token_date
)
UPDATE tokens t
SET token_seq = m.max_seq + r.rn - 1
FROM ranked r
JOIN maxes m
  ON m.branch_id = r.branch_id
 AND m.priority  = r.priority
 AND m.token_date = r.token_date
WHERE t.id = r.id
  AND r.rn > 1;

ALTER TABLE tokens
    DROP CONSTRAINT IF EXISTS uk_tokens_branch_priority_seq_date;
ALTER TABLE tokens
    ADD CONSTRAINT uk_tokens_branch_priority_seq_date
    UNIQUE (branch_id, priority, token_seq, token_date);

CREATE INDEX IF NOT EXISTS ix_tokens_branch_status_priority_created
    ON tokens (branch_id, status, priority, created_at);
CREATE INDEX IF NOT EXISTS ix_tokens_counter_status
    ON tokens (assigned_counter_id, status);
CREATE INDEX IF NOT EXISTS ix_tokens_branch_created
    ON tokens (branch_id, created_at);

-- findNextToken orders by priority DESC, is_transfer DESC, created_at ASC and
-- takes the first WAITING row. A partial index means that lookup - which runs
-- under a pessimistic write lock on the busiest endpoint - walks an index
-- instead of scanning and sorting the whole table.
CREATE INDEX IF NOT EXISTS ix_tokens_waiting_order
    ON tokens (branch_id, priority DESC, is_transfer DESC, created_at ASC)
    WHERE status = 'WAITING';

-- ---------------------------------------------------------------------------
-- everything else that is filtered on but was never indexed
-- ---------------------------------------------------------------------------
CREATE INDEX IF NOT EXISTS ix_counters_branch ON counters (branch_id);
CREATE INDEX IF NOT EXISTS ix_counters_code ON counters (code);
CREATE INDEX IF NOT EXISTS ix_services_branch ON services (branch_id);
CREATE INDEX IF NOT EXISTS ix_feedback_counter ON feedback (counter_id);
CREATE INDEX IF NOT EXISTS ix_feedback_created ON feedback (created_at);
CREATE INDEX IF NOT EXISTS ix_responses_submitted_at ON responses (submitted_at);

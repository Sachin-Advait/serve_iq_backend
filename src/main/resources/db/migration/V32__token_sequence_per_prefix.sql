-- Token numbers now count per prefix (A001, B001, A002, C001) instead of one
-- shared counter per priority (A001, B002, A003). The prefix is stored so the
-- daily sequence and its uniqueness can be scoped to it.
ALTER TABLE tokens ADD COLUMN IF NOT EXISTS token_prefix VARCHAR(20);

-- Existing tickets: the prefix is the token with its trailing number removed.
UPDATE tokens
SET token_prefix = UPPER(TRIM(REGEXP_REPLACE(token, '[0-9]+$', '')))
WHERE token_prefix IS NULL;
UPDATE tokens SET token_prefix = '?' WHERE token_prefix IS NULL OR token_prefix = '';
ALTER TABLE tokens ALTER COLUMN token_prefix SET NOT NULL;

-- Old rows could only clash on the new key if one prefix was issued under two
-- priorities on the same day. Move any such duplicate above that prefix's max
-- for the day, keeping the earliest row on its original number.
WITH ranked AS (
    SELECT id,
           branch_id,
           token_prefix,
           token_date,
           created_at,
           ROW_NUMBER() OVER (PARTITION BY branch_id, token_prefix, token_date, token_seq
                              ORDER BY created_at, id) AS rn
    FROM tokens
),
dups AS (
    SELECT id,
           branch_id,
           token_prefix,
           token_date,
           ROW_NUMBER() OVER (PARTITION BY branch_id, token_prefix, token_date
                              ORDER BY created_at, id) AS k
    FROM ranked
    WHERE rn > 1
),
maxes AS (
    SELECT branch_id, token_prefix, token_date, MAX(token_seq) AS max_seq
    FROM tokens
    GROUP BY branch_id, token_prefix, token_date
)
UPDATE tokens t
SET token_seq = m.max_seq + d.k
FROM dups d
JOIN maxes m
  ON m.branch_id = d.branch_id
 AND m.token_prefix = d.token_prefix
 AND m.token_date = d.token_date
WHERE t.id = d.id;

ALTER TABLE tokens
    DROP CONSTRAINT IF EXISTS uk_tokens_branch_priority_seq_date;
ALTER TABLE tokens
    DROP CONSTRAINT IF EXISTS uk_tokens_branch_prefix_date_seq;
-- Column order matches the MAX(token_seq) lookup in TokenIssuer, so the
-- constraint's index serves it.
ALTER TABLE tokens
    ADD CONSTRAINT uk_tokens_branch_prefix_date_seq
    UNIQUE (branch_id, token_prefix, token_date, token_seq);

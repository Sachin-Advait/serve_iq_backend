-- Some databases also carry a hand-made unique index on
-- (branch_id, priority, token_seq, created_at::date), the old one-counter-per-
-- priority rule. With per-prefix numbering B001 clashes with A001 on it, so
-- every ticket after the first prefix of the day fails. The per-prefix
-- constraint from V32 replaces it.
ALTER TABLE tokens DROP CONSTRAINT IF EXISTS uq_token_branch_priority_day_seq;
DROP INDEX IF EXISTS uq_token_branch_priority_day_seq;

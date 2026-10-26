-- Serving time spent at earlier counters before a transfer. A transfer clears
-- start_at/end_at for the next counter, so this keeps the earlier time for the
-- "total serving time" SLA. Tokens from before this start at 0.
ALTER TABLE tokens ADD COLUMN IF NOT EXISTS prior_serving_seconds INTEGER NOT NULL DEFAULT 0;

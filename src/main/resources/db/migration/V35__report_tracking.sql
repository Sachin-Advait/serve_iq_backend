-- Data for the operational reports.
-- Tokens: when the visitor was first called, how many times they were called
-- (recalls, hold and no-show call-backs), and who marked them no-show.
ALTER TABLE tokens ADD COLUMN IF NOT EXISTS first_called_at TIMESTAMP;
ALTER TABLE tokens ADD COLUMN IF NOT EXISTS call_count INTEGER NOT NULL DEFAULT 0;
ALTER TABLE tokens ADD COLUMN IF NOT EXISTS no_show_count INTEGER NOT NULL DEFAULT 0;
ALTER TABLE tokens ADD COLUMN IF NOT EXISTS no_show_counter_id VARCHAR(255);
ALTER TABLE tokens ADD COLUMN IF NOT EXISTS no_show_user_id VARCHAR(255);

-- Older tokens: a token that reached a counter was called at least once.
UPDATE tokens SET call_count = 1 WHERE call_count = 0 AND assigned_counter_id IS NOT NULL;
UPDATE tokens SET no_show_count = 1, no_show_counter_id = assigned_counter_id, no_show_user_id = served_by_user_id
WHERE status = 'NO_SHOW' AND no_show_count = 0;

-- Counter breaks: one row per pause, closed when the counter is resumed or released.
CREATE TABLE IF NOT EXISTS counter_breaks (
    id          VARCHAR(255) PRIMARY KEY,
    counter_id  VARCHAR(255) NOT NULL,
    user_id     VARCHAR(255),
    branch_id   VARCHAR(255),
    started_at  TIMESTAMP NOT NULL,
    ended_at    TIMESTAMP
);
CREATE INDEX IF NOT EXISTS ix_counter_breaks_branch_started ON counter_breaks (branch_id, started_at);
CREATE INDEX IF NOT EXISTS ix_counter_breaks_open ON counter_breaks (counter_id) WHERE ended_at IS NULL;

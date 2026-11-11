-- When the ticket was last marked no-show, so the agent's No-Show list can
-- drop it after app.no-show.expiry-minutes. Null for older tickets.
ALTER TABLE tokens ADD COLUMN IF NOT EXISTS no_show_at TIMESTAMP;

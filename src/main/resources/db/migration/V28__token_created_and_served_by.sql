-- Who issued each ticket (kiosk or receptionist user) and which agent served
-- it. Only user ids are stored; names and emails come from the users table.
ALTER TABLE tokens ADD COLUMN IF NOT EXISTS created_by_user_id VARCHAR(255);
ALTER TABLE tokens ADD COLUMN IF NOT EXISTS served_by_user_id VARCHAR(255);
CREATE INDEX IF NOT EXISTS idx_tokens_created_by_user_id ON tokens (created_by_user_id);
CREATE INDEX IF NOT EXISTS idx_tokens_served_by_user_id ON tokens (served_by_user_id);

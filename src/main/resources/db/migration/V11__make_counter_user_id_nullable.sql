-- Make user_id column nullable in counters table
ALTER TABLE counters ALTER COLUMN user_id DROP NOT NULL;
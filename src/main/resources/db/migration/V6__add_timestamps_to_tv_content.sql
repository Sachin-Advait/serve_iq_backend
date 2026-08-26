-- Add timestamps to tv_content table
ALTER TABLE tv_content ADD COLUMN IF NOT EXISTS created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE tv_content ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP;

-- Update existing rows
UPDATE tv_content SET created_at = CURRENT_TIMESTAMP WHERE created_at IS NULL;
UPDATE tv_content SET updated_at = CURRENT_TIMESTAMP WHERE updated_at IS NULL;

-- Make NOT NULL
ALTER TABLE tv_content ALTER COLUMN created_at SET NOT NULL;
ALTER TABLE tv_content ALTER COLUMN updated_at SET NOT NULL;
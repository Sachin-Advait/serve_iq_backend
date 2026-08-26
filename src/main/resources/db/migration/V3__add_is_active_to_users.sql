-- Add is_active column to users table for AD integration
ALTER TABLE users ADD COLUMN IF NOT EXISTS is_active BOOLEAN DEFAULT true;

-- Update existing users to be active
UPDATE users SET is_active = true WHERE is_active IS NULL;

-- Make it NOT NULL
ALTER TABLE users ALTER COLUMN is_active SET NOT NULL;
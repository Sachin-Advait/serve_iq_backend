-- Add archive and HLS fields to tv_content table
ALTER TABLE tv_content ADD COLUMN IF NOT EXISTS archived BOOLEAN DEFAULT false;
ALTER TABLE tv_content ADD COLUMN IF NOT EXISTS hls_url TEXT;
ALTER TABLE tv_content ADD COLUMN IF NOT EXISTS hls_processed BOOLEAN DEFAULT false;
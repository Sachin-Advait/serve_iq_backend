-- Add foreground_font_color column (TV only field, but exists on both tables)
ALTER TABLE app_configs ADD COLUMN IF NOT EXISTS foreground_font_color VARCHAR(255);
ALTER TABLE app_config_templates ADD COLUMN IF NOT EXISTS foreground_font_color VARCHAR(255);

-- Backfill foreground font color for TV_DISPLAY
UPDATE app_configs
SET foreground_font_color = '#FFFFFF'
WHERE app_type = 'TV_DISPLAY'
  AND foreground_font_color IS NULL;

UPDATE app_config_templates
SET foreground_font_color = '#FFFFFF'
WHERE app_type = 'TV_DISPLAY'
  AND foreground_font_color IS NULL;

-- Update TV_DISPLAY secondary color ONLY if still the old default
UPDATE app_configs
SET secondary_color = '#14100B'
WHERE app_type = 'TV_DISPLAY'
  AND secondary_color = '#9E9B46';

UPDATE app_config_templates
SET secondary_color = '#14100B'
WHERE app_type = 'TV_DISPLAY'
  AND secondary_color = '#9E9B46';

-- Ensure FEEDBACK / KIOSK get white secondary (only if still old default)
UPDATE app_configs
SET secondary_color = '#FFFFFF'
WHERE app_type IN ('FEEDBACK', 'KIOSK')
  AND secondary_color = '#9E9B46';

UPDATE app_config_templates
SET secondary_color = '#FFFFFF'
WHERE app_type IN ('FEEDBACK', 'KIOSK')
  AND secondary_color = '#9E9B46';

-- Also fix primary color if it's still the very old one
UPDATE app_configs
SET primary_color = '#3B3121'
WHERE primary_color = '#3e321a' OR primary_color = '#3E321A';

UPDATE app_config_templates
SET primary_color = '#3B3121'
WHERE primary_color = '#3e321a' OR primary_color = '#3E321A';
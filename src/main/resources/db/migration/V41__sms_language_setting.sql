-- SMS language chosen in the admin panel: 'en', 'ar', or NULL to use the
-- language the visitor picked on the kiosk.
ALTER TABLE sms_settings ADD COLUMN IF NOT EXISTS language VARCHAR(2);
ALTER TABLE sms_settings ADD CONSTRAINT sms_settings_language_check
  CHECK (language IS NULL OR language IN ('en', 'ar'));

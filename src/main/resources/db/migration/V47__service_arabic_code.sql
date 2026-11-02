-- Sub-services get an Arabic code (the Arabic letter matching the English
-- code). Main service codes are now filled automatically and hidden.
ALTER TABLE services ADD COLUMN IF NOT EXISTS arabic_code VARCHAR(2);

-- TV board design per template: DARK or LIGHT (null = the TV app's built-in choice).
ALTER TABLE app_configs ADD COLUMN IF NOT EXISTS theme VARCHAR(20);
ALTER TABLE app_config_templates ADD COLUMN IF NOT EXISTS theme VARCHAR(20);

-- The TV and feedback default templates, and their configs while a default
-- template is in use, switch to Arabic.
UPDATE app_config_templates SET app_language = 'ARABIC'
WHERE app_type IN ('TV_DISPLAY', 'FEEDBACK') AND is_default = true;
UPDATE app_configs SET app_language = 'ARABIC'
WHERE app_type IN ('TV_DISPLAY', 'FEEDBACK') AND default_enabled = true;

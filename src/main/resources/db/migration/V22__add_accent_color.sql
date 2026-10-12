-- Accent color: the highlight on the TV board (Now calling banner and the
-- counter being called). Null means the TV derives it from the primary color.
ALTER TABLE app_configs ADD COLUMN IF NOT EXISTS accent_color VARCHAR(255);
ALTER TABLE app_config_templates ADD COLUMN IF NOT EXISTS accent_color VARCHAR(255);

-- Seconds the feedback screen waits for a rating before returning the counter to IDLE
ALTER TABLE app_configs ADD COLUMN IF NOT EXISTS feedback_idle_timeout INTEGER;
ALTER TABLE app_config_templates ADD COLUMN IF NOT EXISTS feedback_idle_timeout INTEGER;

UPDATE app_configs
SET feedback_idle_timeout = 30
WHERE app_type = 'FEEDBACK'
  AND feedback_idle_timeout IS NULL;

UPDATE app_config_templates
SET feedback_idle_timeout = 30
WHERE app_type = 'FEEDBACK'
  AND feedback_idle_timeout IS NULL;

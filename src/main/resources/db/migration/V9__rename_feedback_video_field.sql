-- Rename video_enabled to feedback_video_enabled in app_configs
ALTER TABLE app_configs
RENAME COLUMN video_enabled TO feedback_video_enabled;

-- Rename video_enabled to feedback_video_enabled in app_config_templates
ALTER TABLE app_config_templates
RENAME COLUMN video_enabled TO feedback_video_enabled;
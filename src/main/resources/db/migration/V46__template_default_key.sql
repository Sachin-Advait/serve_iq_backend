-- Built-in default templates are matched by this key, not by name, so a
-- renamed default is not created again. Filled by TemplateService.
ALTER TABLE app_config_templates ADD COLUMN IF NOT EXISTS default_key VARCHAR(40);

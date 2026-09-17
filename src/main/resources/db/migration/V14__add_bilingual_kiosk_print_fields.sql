-- ==================== ADD BILINGUAL KIOSK PRINT COLUMNS ====================
-- Table: app_configs
ALTER TABLE app_configs
    ADD COLUMN IF NOT EXISTS kiosk_header_title_en       VARCHAR(255),
    ADD COLUMN IF NOT EXISTS kiosk_header_title_ar       VARCHAR(255),
    ADD COLUMN IF NOT EXISTS kiosk_header_subtitle_en    VARCHAR(255),
    ADD COLUMN IF NOT EXISTS kiosk_header_subtitle_ar    VARCHAR(255),
    ADD COLUMN IF NOT EXISTS kiosk_token_title_en        VARCHAR(255),
    ADD COLUMN IF NOT EXISTS kiosk_token_title_ar        VARCHAR(255),
    ADD COLUMN IF NOT EXISTS kiosk_service_label_en      VARCHAR(255),
    ADD COLUMN IF NOT EXISTS kiosk_service_label_ar      VARCHAR(255),
    ADD COLUMN IF NOT EXISTS kiosk_date_label_en         VARCHAR(255),
    ADD COLUMN IF NOT EXISTS kiosk_date_label_ar         VARCHAR(255),
    ADD COLUMN IF NOT EXISTS kiosk_time_label_en         VARCHAR(255),
    ADD COLUMN IF NOT EXISTS kiosk_time_label_ar         VARCHAR(255),
    ADD COLUMN IF NOT EXISTS kiosk_waiting_message_en    VARCHAR(500),
    ADD COLUMN IF NOT EXISTS kiosk_waiting_message_ar    VARCHAR(500),
    ADD COLUMN IF NOT EXISTS kiosk_thank_you_message_en  VARCHAR(500),
    ADD COLUMN IF NOT EXISTS kiosk_thank_you_message_ar  VARCHAR(500);

-- Table: app_config_templates
ALTER TABLE app_config_templates
    ADD COLUMN IF NOT EXISTS kiosk_header_title_en       VARCHAR(255),
    ADD COLUMN IF NOT EXISTS kiosk_header_title_ar       VARCHAR(255),
    ADD COLUMN IF NOT EXISTS kiosk_header_subtitle_en    VARCHAR(255),
    ADD COLUMN IF NOT EXISTS kiosk_header_subtitle_ar    VARCHAR(255),
    ADD COLUMN IF NOT EXISTS kiosk_token_title_en        VARCHAR(255),
    ADD COLUMN IF NOT EXISTS kiosk_token_title_ar        VARCHAR(255),
    ADD COLUMN IF NOT EXISTS kiosk_service_label_en      VARCHAR(255),
    ADD COLUMN IF NOT EXISTS kiosk_service_label_ar      VARCHAR(255),
    ADD COLUMN IF NOT EXISTS kiosk_date_label_en         VARCHAR(255),
    ADD COLUMN IF NOT EXISTS kiosk_date_label_ar         VARCHAR(255),
    ADD COLUMN IF NOT EXISTS kiosk_time_label_en         VARCHAR(255),
    ADD COLUMN IF NOT EXISTS kiosk_time_label_ar         VARCHAR(255),
    ADD COLUMN IF NOT EXISTS kiosk_waiting_message_en    VARCHAR(500),
    ADD COLUMN IF NOT EXISTS kiosk_waiting_message_ar    VARCHAR(500),
    ADD COLUMN IF NOT EXISTS kiosk_thank_you_message_en  VARCHAR(500),
    ADD COLUMN IF NOT EXISTS kiosk_thank_you_message_ar  VARCHAR(500);

-- ==================== MIGRATE OLD DATA (single-language → English) ====================
-- Copy old single-language values into the new "_en" columns
UPDATE app_configs SET
    kiosk_header_title_en      = COALESCE(kiosk_header_title_en, kiosk_header_title),
    kiosk_header_subtitle_en   = COALESCE(kiosk_header_subtitle_en, kiosk_header_subtitle),
    kiosk_token_title_en       = COALESCE(kiosk_token_title_en, kiosk_token_title),
    kiosk_service_label_en     = COALESCE(kiosk_service_label_en, kiosk_service_label),
    kiosk_date_label_en        = COALESCE(kiosk_date_label_en, kiosk_date_label),
    kiosk_time_label_en        = COALESCE(kiosk_time_label_en, kiosk_time_label),
    kiosk_waiting_message_en   = COALESCE(kiosk_waiting_message_en, kiosk_waiting_message),
    kiosk_thank_you_message_en = COALESCE(kiosk_thank_you_message_en, kiosk_thank_you_message);

UPDATE app_config_templates SET
    kiosk_header_title_en      = COALESCE(kiosk_header_title_en, kiosk_header_title),
    kiosk_header_subtitle_en   = COALESCE(kiosk_header_subtitle_en, kiosk_header_subtitle),
    kiosk_token_title_en       = COALESCE(kiosk_token_title_en, kiosk_token_title),
    kiosk_service_label_en     = COALESCE(kiosk_service_label_en, kiosk_service_label),
    kiosk_date_label_en        = COALESCE(kiosk_date_label_en, kiosk_date_label),
    kiosk_time_label_en        = COALESCE(kiosk_time_label_en, kiosk_time_label),
    kiosk_waiting_message_en   = COALESCE(kiosk_waiting_message_en, kiosk_waiting_message),
    kiosk_thank_you_message_en = COALESCE(kiosk_thank_you_message_en, kiosk_thank_you_message);

-- ==================== DROP OLD SINGLE-LANGUAGE COLUMNS ====================
-- Only after data is migrated. Comment these out if you want to keep them as backup.
ALTER TABLE app_configs
    DROP COLUMN IF EXISTS kiosk_print_language,
    DROP COLUMN IF EXISTS kiosk_header_title,
    DROP COLUMN IF EXISTS kiosk_header_subtitle,
    DROP COLUMN IF EXISTS kiosk_token_title,
    DROP COLUMN IF EXISTS kiosk_service_label,
    DROP COLUMN IF EXISTS kiosk_date_label,
    DROP COLUMN IF EXISTS kiosk_time_label,
    DROP COLUMN IF EXISTS kiosk_waiting_message,
    DROP COLUMN IF EXISTS kiosk_thank_you_message;

ALTER TABLE app_config_templates
    DROP COLUMN IF EXISTS kiosk_print_language,
    DROP COLUMN IF EXISTS kiosk_header_title,
    DROP COLUMN IF EXISTS kiosk_header_subtitle,
    DROP COLUMN IF EXISTS kiosk_token_title,
    DROP COLUMN IF EXISTS kiosk_service_label,
    DROP COLUMN IF EXISTS kiosk_date_label,
    DROP COLUMN IF EXISTS kiosk_time_label,
    DROP COLUMN IF EXISTS kiosk_waiting_message,
    DROP COLUMN IF EXISTS kiosk_thank_you_message;
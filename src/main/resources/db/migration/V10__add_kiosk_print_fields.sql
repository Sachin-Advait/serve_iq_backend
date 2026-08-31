-- Add KIOSK PRINT fields to app_configs table
ALTER TABLE app_configs
ADD COLUMN kiosk_print_enabled BOOLEAN DEFAULT true,
ADD COLUMN kiosk_print_language VARCHAR(50) DEFAULT 'ENGLISH',
ADD COLUMN kiosk_header_title VARCHAR(255),
ADD COLUMN kiosk_header_subtitle VARCHAR(255),
ADD COLUMN kiosk_token_title VARCHAR(255),
ADD COLUMN kiosk_service_label VARCHAR(255),
ADD COLUMN kiosk_date_label VARCHAR(100),
ADD COLUMN kiosk_time_label VARCHAR(100),
ADD COLUMN kiosk_waiting_message TEXT,
ADD COLUMN kiosk_thank_you_message TEXT;

-- Add KIOSK PRINT fields to app_config_templates table
ALTER TABLE app_config_templates
ADD COLUMN kiosk_print_enabled BOOLEAN DEFAULT true,
ADD COLUMN kiosk_print_language VARCHAR(50) DEFAULT 'ENGLISH',
ADD COLUMN kiosk_header_title VARCHAR(255),
ADD COLUMN kiosk_header_subtitle VARCHAR(255),
ADD COLUMN kiosk_token_title VARCHAR(255),
ADD COLUMN kiosk_service_label VARCHAR(255),
ADD COLUMN kiosk_date_label VARCHAR(100),
ADD COLUMN kiosk_time_label VARCHAR(100),
ADD COLUMN kiosk_waiting_message TEXT,
ADD COLUMN kiosk_thank_you_message TEXT;
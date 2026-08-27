-- Create app_configs table
CREATE TABLE IF NOT EXISTS app_configs (
    id VARCHAR(255) PRIMARY KEY,
    app_type VARCHAR(50) NOT NULL UNIQUE,
    background_image TEXT,
    app_logo TEXT,
    background_image_url TEXT,
    app_logo_url TEXT,
    app_language VARCHAR(20) DEFAULT 'ENGLISH',
    default_enabled BOOLEAN DEFAULT true,
    primary_color VARCHAR(20) DEFAULT '#3e321a',
    secondary_color VARCHAR(20) DEFAULT '#9E9B46',
    ticker_enabled BOOLEAN,
    ticker_speed INTEGER,
    videos_enabled BOOLEAN,
    flicker_time INTEGER,
    image_carousel_enabled BOOLEAN,
    video_enabled BOOLEAN
);

-- Create app_config_component_order table
CREATE TABLE IF NOT EXISTS app_config_component_order (
    config_id VARCHAR(255) NOT NULL,
    component VARCHAR(50) NOT NULL,
    position INTEGER NOT NULL,
    PRIMARY KEY (config_id, position),
    FOREIGN KEY (config_id) REFERENCES app_configs(id) ON DELETE CASCADE
);

-- Create app_config_templates table
CREATE TABLE IF NOT EXISTS app_config_templates (
    id VARCHAR(255) PRIMARY KEY,
    app_type VARCHAR(50) NOT NULL,
    template_name VARCHAR(255) NOT NULL,
    description TEXT,
    is_active BOOLEAN DEFAULT false,
    is_default BOOLEAN DEFAULT false,
    background_image TEXT,
    app_logo TEXT,
    background_image_url TEXT,
    app_logo_url TEXT,
    app_language VARCHAR(20),
    default_enabled BOOLEAN,
    primary_color VARCHAR(20),
    secondary_color VARCHAR(20),
    ticker_enabled BOOLEAN,
    ticker_speed INTEGER,
    videos_enabled BOOLEAN,
    flicker_time INTEGER,
    image_carousel_enabled BOOLEAN,
    video_enabled BOOLEAN,
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

-- Create app_config_template_component_order table
CREATE TABLE IF NOT EXISTS app_config_template_component_order (
    template_id VARCHAR(255) NOT NULL,
    component VARCHAR(50) NOT NULL,
    position INTEGER NOT NULL,
    PRIMARY KEY (template_id, position),
    FOREIGN KEY (template_id) REFERENCES app_config_templates(id) ON DELETE CASCADE
);

-- Create indexes
CREATE INDEX IF NOT EXISTS idx_app_config_templates_app_type ON app_config_templates(app_type);
CREATE INDEX IF NOT EXISTS idx_app_config_templates_active ON app_config_templates(app_type, is_active);
CREATE INDEX IF NOT EXISTS idx_app_config_templates_default ON app_config_templates(app_type, is_default);
CREATE TABLE IF NOT EXISTS breaking_news (
    id VARCHAR(36) PRIMARY KEY,
    title VARCHAR(500),
    description TEXT,
    link TEXT,
    source VARCHAR(255),
    category VARCHAR(255),
    type VARCHAR(50),
    published_date TIMESTAMP,
    published BOOLEAN DEFAULT false,
    active BOOLEAN DEFAULT true,
    archived BOOLEAN DEFAULT false,
    separator_image TEXT,
    separator_image_url TEXT,
    display_order INTEGER DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS news_source_config (
    id VARCHAR(36) PRIMARY KEY,
    source_name VARCHAR(255) UNIQUE,
    rss_url TEXT,
    category VARCHAR(255),
    active BOOLEAN DEFAULT true,
    max_items INTEGER DEFAULT 5,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_news_published ON breaking_news(published, active, archived);
CREATE INDEX IF NOT EXISTS idx_news_published_date ON breaking_news(published_date);
CREATE INDEX IF NOT EXISTS idx_news_type ON breaking_news(type);

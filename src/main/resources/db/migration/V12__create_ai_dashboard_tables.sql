-- =====================================================
-- Visitor Management AI Dashboard Tables
-- =====================================================
-- IMPORTANT: This keeps the structure agreed with the existing Python analytics package.
-- New persistent objects created: 2 tables only.
-- =====================================================

-- =====================================================
-- Table 1: Current AI Dashboard (Latest Results)
-- =====================================================
CREATE TABLE IF NOT EXISTS ai_dashboard_current (
    system_code    VARCHAR(64)  NOT NULL,
    use_case_code  VARCHAR(96)  NOT NULL,
    frequency      VARCHAR(16)  NOT NULL,
    generated_at   TIMESTAMPTZ  NOT NULL,
    valid_from     TIMESTAMPTZ,
    valid_to       TIMESTAMPTZ,
    status         VARCHAR(24)  NOT NULL DEFAULT 'READY',
    model_name     VARCHAR(160),
    model_version  VARCHAR(40),
    payload        JSONB        NOT NULL,
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    PRIMARY KEY (system_code, use_case_code, frequency)
);

-- =====================================================
-- Table 2: AI Dashboard History (Append-Only)
-- =====================================================
CREATE TABLE IF NOT EXISTS ai_dashboard_history (
    history_id     BIGSERIAL    PRIMARY KEY,
    system_code    VARCHAR(64)  NOT NULL,
    use_case_code  VARCHAR(96)  NOT NULL,
    frequency      VARCHAR(16)  NOT NULL,
    generated_at   TIMESTAMPTZ  NOT NULL,
    valid_from     TIMESTAMPTZ,
    valid_to       TIMESTAMPTZ,
    status         VARCHAR(24)  NOT NULL DEFAULT 'READY',
    model_name     VARCHAR(160),
    model_version  VARCHAR(40),
    payload        JSONB        NOT NULL,
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- =====================================================
-- Indexes for Performance Optimization
-- =====================================================

-- Index for current table: Fast lookup by system_code, frequency, and latest generated_at
CREATE INDEX IF NOT EXISTS idx_ai_dashboard_current_lookup
    ON ai_dashboard_current(system_code, frequency, generated_at DESC);

-- Index for history table: Fast lookup by system_code, use_case_code, frequency, and latest generated_at
CREATE INDEX IF NOT EXISTS idx_ai_dashboard_history_lookup
    ON ai_dashboard_history(system_code, use_case_code, frequency, generated_at DESC);

-- Additional index for history: Lookup by system_code and frequency only
CREATE INDEX IF NOT EXISTS idx_ai_dashboard_history_system_frequency
    ON ai_dashboard_history(system_code, frequency, generated_at DESC);

-- Additional index for history: Lookup by status
CREATE INDEX IF NOT EXISTS idx_ai_dashboard_history_status
    ON ai_dashboard_history(status, generated_at DESC);

-- =====================================================
-- Table Comments
-- =====================================================

COMMENT ON TABLE ai_dashboard_current IS 'Latest successful Python analytics result; Java/React read model.';
COMMENT ON TABLE ai_dashboard_history IS 'Append-only analytics snapshots for history/audit/accuracy analysis.';

-- Column comments for ai_dashboard_current
COMMENT ON COLUMN ai_dashboard_current.system_code IS 'System identifier (e.g., VISITOR_MANAGEMENT)';
COMMENT ON COLUMN ai_dashboard_current.use_case_code IS 'Use case identifier (e.g., DASHBOARD_OVERVIEW, KPI_METRICS)';
COMMENT ON COLUMN ai_dashboard_current.frequency IS 'Update frequency (e.g., REALTIME, HOURLY, DAILY)';
COMMENT ON COLUMN ai_dashboard_current.generated_at IS 'Timestamp when the analytics were generated';
COMMENT ON COLUMN ai_dashboard_current.valid_from IS 'Start of validity period (nullable)';
COMMENT ON COLUMN ai_dashboard_current.valid_to IS 'End of validity period (nullable)';
COMMENT ON COLUMN ai_dashboard_current.status IS 'Status: READY, PROCESSING, FAILED, STALE';
COMMENT ON COLUMN ai_dashboard_current.model_name IS 'Name of the AI/ML model used';
COMMENT ON COLUMN ai_dashboard_current.model_version IS 'Version of the AI/ML model used';
COMMENT ON COLUMN ai_dashboard_current.payload IS 'JSON payload containing analytics results';
COMMENT ON COLUMN ai_dashboard_current.updated_at IS 'Timestamp when the record was last updated';

-- Column comments for ai_dashboard_history
COMMENT ON COLUMN ai_dashboard_history.history_id IS 'Auto-incrementing unique identifier';
COMMENT ON COLUMN ai_dashboard_history.system_code IS 'System identifier (e.g., VISITOR_MANAGEMENT)';
COMMENT ON COLUMN ai_dashboard_history.use_case_code IS 'Use case identifier (e.g., DASHBOARD_OVERVIEW, KPI_METRICS)';
COMMENT ON COLUMN ai_dashboard_history.frequency IS 'Update frequency (e.g., REALTIME, HOURLY, DAILY)';
COMMENT ON COLUMN ai_dashboard_history.generated_at IS 'Timestamp when the analytics were generated';
COMMENT ON COLUMN ai_dashboard_history.valid_from IS 'Start of validity period (nullable)';
COMMENT ON COLUMN ai_dashboard_history.valid_to IS 'End of validity period (nullable)';
COMMENT ON COLUMN ai_dashboard_history.status IS 'Status: READY, PROCESSING, FAILED, STALE';
COMMENT ON COLUMN ai_dashboard_history.model_name IS 'Name of the AI/ML model used';
COMMENT ON COLUMN ai_dashboard_history.model_version IS 'Version of the AI/ML model used';
COMMENT ON COLUMN ai_dashboard_history.payload IS 'JSON payload containing analytics results';
COMMENT ON COLUMN ai_dashboard_history.updated_at IS 'Timestamp when the record was last updated';

-- =====================================================
-- Optional: Add constraint to ensure valid status values
-- =====================================================
ALTER TABLE ai_dashboard_current
    ADD CONSTRAINT chk_ai_dashboard_current_status
    CHECK (status IN ('READY', 'PROCESSING', 'FAILED', 'STALE'));

ALTER TABLE ai_dashboard_history
    ADD CONSTRAINT chk_ai_dashboard_history_status
    CHECK (status IN ('READY', 'PROCESSING', 'FAILED', 'STALE'));

-- =====================================================
-- Optional: Add constraint to ensure valid frequency values
-- =====================================================
ALTER TABLE ai_dashboard_current
    ADD CONSTRAINT chk_ai_dashboard_current_frequency
    CHECK (frequency IN ('REALTIME', 'HOURLY', 'DAILY', 'WEEKLY', 'MONTHLY'));

ALTER TABLE ai_dashboard_history
    ADD CONSTRAINT chk_ai_dashboard_history_frequency
    CHECK (frequency IN ('REALTIME', 'HOURLY', 'DAILY', 'WEEKLY', 'MONTHLY'));
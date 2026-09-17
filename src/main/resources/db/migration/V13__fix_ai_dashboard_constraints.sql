-- Drop the redundant index
DROP INDEX IF EXISTS idx_ai_dashboard_history_system_frequency;

-- Make constraints idempotent
ALTER TABLE ai_dashboard_current DROP CONSTRAINT IF EXISTS chk_ai_dashboard_current_status;
ALTER TABLE ai_dashboard_current ADD CONSTRAINT chk_ai_dashboard_current_status
    CHECK (status IN ('READY','PROCESSING','FAILED','STALE'));

ALTER TABLE ai_dashboard_history DROP CONSTRAINT IF EXISTS chk_ai_dashboard_history_status;
ALTER TABLE ai_dashboard_history ADD CONSTRAINT chk_ai_dashboard_history_status
    CHECK (status IN ('READY','PROCESSING','FAILED','STALE'));

ALTER TABLE ai_dashboard_current DROP CONSTRAINT IF EXISTS chk_ai_dashboard_current_frequency;
ALTER TABLE ai_dashboard_current ADD CONSTRAINT chk_ai_dashboard_current_frequency
    CHECK (frequency IN ('HOURLY','DAILY'));   -- tightened to match Java contract

ALTER TABLE ai_dashboard_history DROP CONSTRAINT IF EXISTS chk_ai_dashboard_history_frequency;
ALTER TABLE ai_dashboard_history ADD CONSTRAINT chk_ai_dashboard_history_frequency
    CHECK (frequency IN ('HOURLY','DAILY'));
-- Service level agreement: the longest a visitor should wait between taking a
-- ticket and being called. NULL on a sub-service means "use the parent's".
ALTER TABLE services ADD COLUMN IF NOT EXISTS sla_seconds INTEGER;

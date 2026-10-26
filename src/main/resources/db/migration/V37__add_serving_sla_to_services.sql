-- Serving-time SLA: the longest serving should take, from start of serving to
-- completion. NULL on a sub-service means "use the parent's" (default 10 min).
ALTER TABLE services ADD COLUMN IF NOT EXISTS serving_sla_seconds INTEGER;

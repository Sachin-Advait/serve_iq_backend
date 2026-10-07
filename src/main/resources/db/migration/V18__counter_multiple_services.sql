-- A counter can now handle several services, and a service can be on several counters.
-- Counter -> services lives in counters.service_ids; service -> counters stays in
-- services.counter_ids. Both are comma-separated id lists, so widen them past 255 chars.
ALTER TABLE services ALTER COLUMN counter_ids TYPE TEXT;
ALTER TABLE tokens ALTER COLUMN counter_ids TYPE TEXT;
ALTER TABLE counters ADD COLUMN IF NOT EXISTS service_ids TEXT;

-- Backfill counters.service_ids from the old single service_id plus any service
-- that already lists the counter in its counter_ids.
UPDATE counters c
SET service_ids = sub.ids
FROM (
    SELECT c2.id AS counter_id, string_agg(DISTINCT s.id::text, ',') AS ids
    FROM counters c2
    JOIN services s
      ON s.id = c2.service_id
      OR (',' || COALESCE(s.counter_ids, '') || ',') LIKE ('%,' || c2.id || ',%')
    GROUP BY c2.id
) sub
WHERE c.id = sub.counter_id;

-- Make services.counter_ids the mirror of counters.service_ids. Before this, a
-- service assigned from the counter screen had no counters of its own, so its
-- tokens went to every counter.
UPDATE services s
SET counter_ids = sub.ids
FROM (
    SELECT s2.id AS service_id, string_agg(DISTINCT c.id::text, ',') AS ids
    FROM services s2
    JOIN counters c
      ON (',' || COALESCE(c.service_ids, '') || ',') LIKE ('%,' || s2.id || ',%')
    GROUP BY s2.id
) sub
WHERE s.id = sub.service_id;

-- Keep service_id pointing at the counter's first service for older clients.
UPDATE counters
SET service_id = split_part(service_ids, ',', 1)
WHERE service_ids IS NOT NULL
  AND (service_id IS NULL OR service_id = '');

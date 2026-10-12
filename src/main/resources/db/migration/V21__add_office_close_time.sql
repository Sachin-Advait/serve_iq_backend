-- When the office closes. Ticket issuing stops on its own once the people
-- already waiting for a service can't be served before this time.
ALTER TABLE branches ADD COLUMN IF NOT EXISTS office_close_time TIME;

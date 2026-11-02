-- Arabic form of a ticket number: the number then the sub-service's Arabic
-- code (001أ for A001). Null for older tickets and category tickets.
ALTER TABLE tokens ADD COLUMN IF NOT EXISTS arabic_token VARCHAR(20);

-- Login tokens issued before this time are rejected; set when an admin force-releases the agent's counter.
ALTER TABLE users ADD COLUMN IF NOT EXISTS tokens_valid_after TIMESTAMP;

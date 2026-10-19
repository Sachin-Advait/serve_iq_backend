-- Optional note the agent writes while serving a visitor, saved on completion.
ALTER TABLE tokens ADD COLUMN IF NOT EXISTS agent_note VARCHAR(1000);

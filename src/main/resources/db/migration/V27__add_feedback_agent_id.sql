-- Feedback is now credited to the agent logged in at the counter, by user id,
-- instead of a name the feedback app sent.
ALTER TABLE feedback ADD COLUMN IF NOT EXISTS agent_id VARCHAR(255);
CREATE INDEX IF NOT EXISTS idx_feedback_agent_id ON feedback (agent_id);

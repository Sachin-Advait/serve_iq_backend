-- Why a token was moved to another counter (procedure 9.g: transfers must record a reason).
ALTER TABLE tokens ADD COLUMN IF NOT EXISTS transfer_reason VARCHAR(50);

-- Ticket issuing control per branch (procedure 10): the supervisor can stop new
-- tickets, and tickets are only issued inside the branch's issuing hours.
ALTER TABLE branches ADD COLUMN IF NOT EXISTS ticket_issuing_enabled BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE branches ADD COLUMN IF NOT EXISTS ticket_issuing_start TIME;
ALTER TABLE branches ADD COLUMN IF NOT EXISTS ticket_issuing_end TIME;
ALTER TABLE branches ADD COLUMN IF NOT EXISTS ticket_stop_message_en VARCHAR(500);
ALTER TABLE branches ADD COLUMN IF NOT EXISTS ticket_stop_message_ar VARCHAR(500);

-- Paper tickets have no mobile number; only green (SMS) tickets do.
ALTER TABLE tokens ALTER COLUMN mobile_number DROP NOT NULL;

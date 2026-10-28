-- AGENT: counter agent who signs in to the agent app; has everything USER has.
ALTER TABLE users DROP CONSTRAINT IF EXISTS users_role_check;
ALTER TABLE users ADD CONSTRAINT users_role_check
  CHECK (role IN ('ADMIN','USER','MANAGER','DISPLAY','KIOSK','RECEPTIONIST','FEEDBACK','SUPERVISOR','AGENT'));
ALTER TABLE user_sessions DROP CONSTRAINT IF EXISTS user_sessions_role_check;

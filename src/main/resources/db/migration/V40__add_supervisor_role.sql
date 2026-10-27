-- SUPERVISOR: uses the admin panel like a manager, without Login History.
ALTER TABLE users DROP CONSTRAINT IF EXISTS users_role_check;
ALTER TABLE users ADD CONSTRAINT users_role_check
  CHECK (role IN ('ADMIN','USER','MANAGER','DISPLAY','KIOSK','RECEPTIONIST','FEEDBACK','SUPERVISOR'));

-- Login history rows carry the role too. V29 adds no check here, but a database
-- whose table was made by Hibernate has one listing the old roles.
ALTER TABLE user_sessions DROP CONSTRAINT IF EXISTS user_sessions_role_check;

-- One row per login of an agent (USER), ADMIN or RECEPTIONIST: check-in time,
-- check-out time and how the session ended (MANUAL logout, AUTO daily sign-out,
-- or NEW_LOGIN when the user logged in again before logging out).
CREATE TABLE IF NOT EXISTS user_sessions (
    id          VARCHAR(255) PRIMARY KEY,
    user_id     VARCHAR(255) NOT NULL,
    role        VARCHAR(50)  NOT NULL,
    branch_id   VARCHAR(255),
    counter_id  VARCHAR(255),
    login_at    TIMESTAMP    NOT NULL,
    logout_at   TIMESTAMP,
    logout_type VARCHAR(20)
);
CREATE INDEX IF NOT EXISTS idx_user_sessions_user_id ON user_sessions (user_id);
CREATE INDEX IF NOT EXISTS idx_user_sessions_login_at ON user_sessions (login_at);
CREATE INDEX IF NOT EXISTS idx_user_sessions_open ON user_sessions (logout_at) WHERE logout_at IS NULL;

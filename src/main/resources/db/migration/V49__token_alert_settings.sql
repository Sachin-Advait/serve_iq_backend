-- Waiting-token alert mail, configured from the admin panel's System page.
-- One row (id = 1). When a branch's waiting tokens reach waiting_threshold,
-- active users with one of the roles (comma list, e.g. ADMIN,MANAGER) get one
-- e-mail; the next mail goes out only after the queue drops below it again.
CREATE TABLE IF NOT EXISTS token_alert_settings (
    id                INTEGER PRIMARY KEY,
    enabled           BOOLEAN      NOT NULL DEFAULT TRUE,
    waiting_threshold INTEGER      NOT NULL DEFAULT 20,
    roles             VARCHAR(255),
    updated_at        TIMESTAMP
);

INSERT INTO token_alert_settings (id, enabled, waiting_threshold, roles, updated_at)
SELECT 1, TRUE, 20, 'ADMIN', NOW()
WHERE NOT EXISTS (SELECT 1 FROM token_alert_settings WHERE id = 1);

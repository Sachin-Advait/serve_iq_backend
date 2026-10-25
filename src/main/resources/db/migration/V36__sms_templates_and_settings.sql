-- SMS text the admin edits from the admin panel. One row per message the
-- backend sends; the code is what the backend looks up, so it never changes.
-- Placeholders such as {token} are filled in when the SMS is sent.
CREATE TABLE IF NOT EXISTS sms_templates (
    id          VARCHAR(255) PRIMARY KEY,
    code        VARCHAR(50)  NOT NULL UNIQUE,
    name        VARCHAR(255) NOT NULL,
    description VARCHAR(500),
    body_en     TEXT         NOT NULL,
    body_ar     TEXT         NOT NULL,
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP,
    updated_at  TIMESTAMP
);

INSERT INTO sms_templates (id, code, name, description, body_en, body_ar, active, created_at, updated_at)
SELECT 'sms-template-token-receipt', 'TOKEN_RECEIPT', 'Ticket receipt',
       'Sent when the kiosk issues a green (SMS) ticket with a mobile number.',
       E'MSSPF\nVisitor Management\n\nQueue Token: {token}\nService: {service}\nCurrent Queue: {queue}\nDate: {date}\nTime: {time}\n\nPlease wait for your number to be called.\nThank you.',
       E'MSSPF\nنظام إدارة الزوار\n\nرقم الانتظار: {token}\nالخدمة: {service}\nالمنتظرون حالياً: {queue}\nالتاريخ: {date}\nالوقت: {time}\n\nيرجى انتظار نداء الرقم\nشكراً لك',
       TRUE, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM sms_templates WHERE code = 'TOKEN_RECEIPT');

-- Non-secret SMS settings the admin can change; a single row (id = 1).
-- Empty sender/priority/app_id fall back to the SMSOMAN_* environment values.
-- The SMSOMAN username, password and URL stay in the environment only.
CREATE TABLE IF NOT EXISTS sms_settings (
    id         INTEGER PRIMARY KEY,
    enabled    BOOLEAN NOT NULL DEFAULT TRUE,
    sender     VARCHAR(11),
    priority   INTEGER,
    app_id     VARCHAR(50),
    updated_at TIMESTAMP
);

INSERT INTO sms_settings (id, enabled, updated_at)
SELECT 1, TRUE, NOW()
WHERE NOT EXISTS (SELECT 1 FROM sms_settings WHERE id = 1);

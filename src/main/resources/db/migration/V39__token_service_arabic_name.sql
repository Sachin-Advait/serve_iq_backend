-- The service's Arabic name, copied onto the token like service_name, so the
-- agent app can show it in Arabic. Existing tokens are filled from services.
ALTER TABLE tokens ADD COLUMN IF NOT EXISTS service_arabic_name VARCHAR(255);
UPDATE tokens SET service_arabic_name =
    (SELECT s.arabic_name FROM services s WHERE s.id = tokens.service_id)
WHERE service_arabic_name IS NULL;

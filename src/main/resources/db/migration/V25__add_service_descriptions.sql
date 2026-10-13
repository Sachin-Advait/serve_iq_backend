-- Optional English and Arabic descriptions shown under a service on the kiosk
ALTER TABLE services
    ADD COLUMN IF NOT EXISTS description        VARCHAR(1000),
    ADD COLUMN IF NOT EXISTS arabic_description VARCHAR(1000);

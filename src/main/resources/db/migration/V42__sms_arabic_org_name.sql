-- The Arabic ticket SMS opened with the English "MSSPF"; use the Arabic name.
UPDATE sms_templates
SET body_ar = 'صندوق تقاعد الأجهزة العسكرية والأمنية' || substring(body_ar FROM 6)
WHERE code = 'TOKEN_RECEIPT' AND body_ar LIKE 'MSSPF%';

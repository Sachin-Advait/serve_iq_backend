-- Arabic ticket SMS header: "إم إس إس بي إف" (MSSPF in Arabic letters),
-- replacing "MSSPF" or the full fund name V42 put there.
UPDATE sms_templates
SET body_ar = 'إم إس إس بي إف' || substring(body_ar FROM length('MSSPF') + 1)
WHERE code = 'TOKEN_RECEIPT' AND body_ar LIKE 'MSSPF%';

UPDATE sms_templates
SET body_ar = 'إم إس إس بي إف'
    || substring(body_ar FROM length('صندوق تقاعد الأجهزة العسكرية والأمنية') + 1)
WHERE code = 'TOKEN_RECEIPT' AND body_ar LIKE 'صندوق تقاعد الأجهزة العسكرية والأمنية%';

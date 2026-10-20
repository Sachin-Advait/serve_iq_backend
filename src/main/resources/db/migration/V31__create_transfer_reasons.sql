-- Transfer reasons the agent picks from, managed by the admin with English and
-- Arabic titles. tokens.transfer_reason keeps storing the code, so the eight
-- reasons below keep the codes the old fixed list used.
CREATE TABLE IF NOT EXISTS transfer_reasons (
    id         VARCHAR(255) PRIMARY KEY,
    code       VARCHAR(50)  NOT NULL UNIQUE,
    title_en   VARCHAR(255) NOT NULL,
    title_ar   VARCHAR(255) NOT NULL,
    active     BOOLEAN      NOT NULL DEFAULT TRUE,
    sort_order INTEGER      NOT NULL DEFAULT 0,
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

INSERT INTO transfer_reasons (id, code, title_en, title_ar, active, sort_order, created_at, updated_at) VALUES
    ('tr-wrong-service',       'WRONG_SERVICE',       'Visitor selected the wrong service',                 'اختار الزائر خدمة غير صحيحة',                    TRUE, 1, NOW(), NOW()),
    ('tr-needs-specialist',    'NEEDS_SPECIALIST',    'Needs a specialist officer',                         'يحتاج إلى موظف مختص',                             TRUE, 2, NOW(), NOW()),
    ('tr-missing-documents',   'MISSING_DOCUMENTS',   'Required documents are missing',                     'المستندات المطلوبة غير مكتملة',                   TRUE, 3, NOW(), NOW()),
    ('tr-other-section',       'OTHER_SECTION',       'Needs to consult another section',                   'يحتاج إلى مراجعة قسم آخر',                        TRUE, 4, NOW(), NOW()),
    ('tr-misclassified',       'MISCLASSIFIED',       'Service was classified incorrectly at ticket issue', 'تم تصنيف الخدمة بشكل غير صحيح عند إصدار التذكرة', TRUE, 5, NOW(), NOW()),
    ('tr-alternative-channel', 'ALTERNATIVE_CHANNEL', 'Directed to an online or Contact Centre channel',    'توجيه إلى القنوات الإلكترونية أو مركز الاتصال',   TRUE, 6, NOW(), NOW()),
    ('tr-crowd-management',    'CROWD_MANAGEMENT',    'Crowd management',                                   'إدارة الازدحام',                                  TRUE, 7, NOW(), NOW()),
    ('tr-other',               'OTHER',               'Other',                                              'أخرى',                                            TRUE, 8, NOW(), NOW())
ON CONFLICT (code) DO NOTHING;

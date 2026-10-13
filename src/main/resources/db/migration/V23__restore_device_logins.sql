-- The daily sign-out used to revoke device accounts too (TV, kiosk, feedback
-- screen), so their saved logins got 403 the next morning. Device accounts
-- are now skipped; this clears the cutoff already stamped on them so their
-- existing logins work again.
UPDATE users SET tokens_valid_after = NULL WHERE role IN ('DISPLAY', 'KIOSK', 'FEEDBACK');

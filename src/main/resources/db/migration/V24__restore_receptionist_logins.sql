-- Receptionists are now also skipped by the daily sign-out; clear the cutoff
-- already stamped on them so their current logins work again.
UPDATE users SET tokens_valid_after = NULL WHERE role = 'RECEPTIONIST';

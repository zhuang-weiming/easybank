UPDATE accounts SET account_number = '67b2617160', account_holder = 'Test User', account_type = 'CHECKING' WHERE id = 7 AND (account_number IS NULL OR account_holder IS NULL OR account_type IS NULL);

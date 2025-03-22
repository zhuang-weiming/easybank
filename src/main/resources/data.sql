-- Insert test data for accounts
INSERT INTO public.accounts (account_number, balance, currency, account_type, status, version)
VALUES
    ('ACC001', 1000.00, 'USD', 'SAVINGS', 'ACTIVE', 0),
    ('ACC002', 2000.00, 'USD', 'CHECKING', 'ACTIVE', 0),
    ('ACC003', 500.00, 'USD', 'SAVINGS', 'ACTIVE', 0);

-- Insert test data for transactions
INSERT INTO public.transactions (source_account_id, destination_account_id, amount, currency, transaction_type, status, description)
VALUES
    (1, 2, 100.00, 'USD', 'TRANSFER', 'COMPLETED', 'Test transfer from ACC001 to ACC002'),
    (2, 3, 50.00, 'USD', 'TRANSFER', 'COMPLETED', 'Test transfer from ACC002 to ACC003');
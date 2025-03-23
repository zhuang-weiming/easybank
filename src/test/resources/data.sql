-- Insert test data for accounts
INSERT INTO account (account_number, account_holder, balance, currency, account_type, version, created_at, updated_at)
VALUES
    ('ACC-' || substr(gen_random_uuid()::text, 1, 8), 'John Doe', 1000.00, 'USD', 'SAVINGS', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('ACC-' || substr(gen_random_uuid()::text, 1, 8), 'Jane Smith', 2000.00, 'USD', 'CHECKING', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('ACC-' || substr(gen_random_uuid()::text, 1, 8), 'Robert Johnson', 500.00, 'USD', 'SAVINGS', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- Insert test data for transactions
INSERT INTO transaction (source_account_id, destination_account_id, amount, currency, transaction_type, status, description, version, created_at, updated_at)
SELECT 
    a1.id as source_account_id,
    a2.id as destination_account_id,
    100.00 as amount,
    'USD' as currency,
    'TRANSFER' as transaction_type,
    'COMPLETED' as status,
    'Test transfer from ' || a1.account_holder || ' to ' || a2.account_holder as description,
    0 as version,
    CURRENT_TIMESTAMP as created_at,
    CURRENT_TIMESTAMP as updated_at
FROM account a1
CROSS JOIN account a2
WHERE a1.account_number LIKE 'ACC-%'
AND a2.account_number LIKE 'ACC-%'
AND a1.id < a2.id
LIMIT 2; 
-- Insert test data for accounts
INSERT INTO public.accounts (account_number, account_holder, balance, currency, account_type, status, version, created_at, updated_at)
VALUES
    ('ACC-' || substr(gen_random_uuid()::text, 1, 8), 'John Doe', 1000.00, 'USD', 'SAVINGS', 'ACTIVE', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('ACC-' || substr(gen_random_uuid()::text, 1, 8), 'Jane Smith', 2000.00, 'USD', 'CHECKING', 'ACTIVE', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('ACC-' || substr(gen_random_uuid()::text, 1, 8), 'Robert Johnson', 500.00, 'USD', 'SAVINGS', 'ACTIVE', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- Insert test data for transactions
INSERT INTO public.transactions (source_account_id, destination_account_id, amount, currency, transaction_type, status, version, created_at, updated_at)
SELECT 
    a1.id as source_account_id,
    a2.id as destination_account_id,
    100.00 as amount,
    'USD' as currency,
    'TRANSFER' as transaction_type,
    'COMPLETED' as status,
    0 as version,
    CURRENT_TIMESTAMP as created_at,
    CURRENT_TIMESTAMP as updated_at
FROM public.accounts a1
CROSS JOIN public.accounts a2
WHERE a1.account_number LIKE 'ACC-%'
AND a2.account_number LIKE 'ACC-%'
AND a1.id < a2.id
LIMIT 2;
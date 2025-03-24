CREATE TABLE IF NOT EXISTS accounts (
    id SERIAL PRIMARY KEY,
    account_number VARCHAR(255) NOT NULL UNIQUE,
    account_holder VARCHAR(255) NOT NULL,
    balance DECIMAL(19, 2) NOT NULL DEFAULT 0.00,
    version BIGINT NOT NULL DEFAULT 0,
    account_type VARCHAR(50) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS transactions (
    id SERIAL PRIMARY KEY,
    source_account_id BIGINT REFERENCES accounts(id),
    destination_account_id BIGINT REFERENCES accounts(id),
    amount DECIMAL(19, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    transaction_type VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    description TEXT,
    version INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_different_accounts CHECK (source_account_id != destination_account_id),
    CONSTRAINT chk_positive_amount CHECK (amount > 0)
);
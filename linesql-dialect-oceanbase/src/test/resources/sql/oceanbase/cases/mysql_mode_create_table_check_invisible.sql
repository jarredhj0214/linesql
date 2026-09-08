CREATE TABLE mart.account_policy (
  id BIGINT NOT NULL,
  amount DECIMAL(18, 2) CHECK (amount >= 0),
  secret_note VARCHAR(255) INVISIBLE,
  CONSTRAINT chk_amount CHECK (amount < 1000000),
  INDEX idx_amount (amount) INVISIBLE
) ENGINE = InnoDB;

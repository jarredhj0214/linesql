ALTER TABLE IF EXISTS mart.orders
ADD COLUMN IF NOT EXISTS total numeric(12, 2)
GENERATED ALWAYS AS (unit_price * quantity) STORED;

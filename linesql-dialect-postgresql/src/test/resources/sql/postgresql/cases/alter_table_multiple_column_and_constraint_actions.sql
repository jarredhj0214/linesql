ALTER TABLE IF EXISTS ONLY mart.orders
    ADD COLUMN gross numeric GENERATED ALWAYS AS (amount + fee) STORED,
    ALTER COLUMN amount SET DATA TYPE numeric(18,2) USING round(amount + conversion_fee, 2),
    ADD CONSTRAINT check_gross CHECK (gross >= 0) NOT VALID,
    ADD CONSTRAINT orders_origin FOREIGN KEY (source_id) REFERENCES raw.orders (id) NOT VALID,
    ALTER COLUMN tax SET EXPRESSION AS (amount * tax_rate),
    DROP COLUMN IF EXISTS ignored;

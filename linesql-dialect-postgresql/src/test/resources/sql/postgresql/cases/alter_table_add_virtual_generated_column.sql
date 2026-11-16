ALTER TABLE mart.metrics ADD COLUMN IF NOT EXISTS net numeric GENERATED ALWAYS AS (amount - discount) VIRTUAL;

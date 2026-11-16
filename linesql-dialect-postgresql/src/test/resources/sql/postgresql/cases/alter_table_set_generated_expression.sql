ALTER TABLE IF EXISTS ONLY mart.metrics ALTER COLUMN net SET EXPRESSION AS (amount - discount);

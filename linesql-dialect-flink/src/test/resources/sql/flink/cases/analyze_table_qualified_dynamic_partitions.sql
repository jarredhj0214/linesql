ANALYZE TABLE lake.staging.orders PARTITION (sold_year=2026, sold_month, sold_day)
COMPUTE STATISTICS FOR COLUMNS order_id, amount;

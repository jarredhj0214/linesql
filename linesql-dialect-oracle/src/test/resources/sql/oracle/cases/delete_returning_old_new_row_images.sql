DELETE FROM mart.orders
WHERE active = 0
RETURNING OLD id, NEW id INTO :original_id, :missing_id;

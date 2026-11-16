DELETE FROM mart.orders o
USING staging.expired e
WHERE o.id = e.order_id AND e.prunable = 1
RETURNING o.id INTO :deleted_id;

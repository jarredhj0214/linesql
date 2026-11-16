DELETE FROM mart.orders o
FROM staging.expired e
WHERE o.id = e.order_id
RETURN o.id BULK COLLECT INTO deleted_ids;

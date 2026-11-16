DELETE FROM mart.orders
USING staging.expired
WHERE orders.id = expired.order_id
RETURNING orders.id, expired.reason AS deleted_reason;

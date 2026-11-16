DELETE FROM mart.orders *
USING staging.expired AS e
WHERE orders.id = e.order_id;

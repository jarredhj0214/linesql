DELETE FROM mart.orders AS "to delete"
USING staging.expired AS e
WHERE "to delete".id = e.order_id AND e.prunable;

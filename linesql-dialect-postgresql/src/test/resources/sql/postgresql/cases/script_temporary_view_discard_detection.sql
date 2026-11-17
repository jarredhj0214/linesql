CREATE TEMP VIEW recent_orders AS
SELECT o.id FROM sales.orders AS o WHERE o.is_recent;
SELECT id FROM recent_orders;
DISCARD TEMP;
SELECT id FROM recent_orders;

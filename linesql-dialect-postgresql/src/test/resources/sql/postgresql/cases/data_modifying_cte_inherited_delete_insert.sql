WITH expired_orders AS (
    SELECT e.order_key FROM staging.expired_orders AS e
    WHERE e.purge_enabled
), moved_orders AS (
    DELETE FROM ONLY sales.orders o
    USING expired_orders AS e
    WHERE o.id = e.order_key
    RETURNING o.id, o.amount
)
INSERT INTO archive.orders (id, amount)
SELECT id, amount FROM moved_orders;

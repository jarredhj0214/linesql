WITH recent AS (
    SELECT o.id AS order_id, o.amount FROM sales.orders AS o WHERE o.active
)
TABLE recent;

WITH ordered AS (
    SELECT order_id AS id, amount FROM sales.orders
), refunded AS (
    SELECT order_id AS id, reason FROM audit.refunds
)
SELECT id, o.amount, r.reason
FROM ordered o NATURAL FULL OUTER JOIN refunded r
WHERE id > 0 ORDER BY id;

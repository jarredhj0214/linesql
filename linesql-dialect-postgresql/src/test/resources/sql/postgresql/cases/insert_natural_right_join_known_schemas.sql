WITH ordered AS (
    SELECT order_id AS id, amount FROM sales.orders
), refunded AS (
    SELECT order_id AS id, reason FROM audit.refunds
)
INSERT INTO mart.matches (match_id, amount, reason)
SELECT id, o.amount, r.reason FROM ordered o NATURAL RIGHT JOIN refunded r;

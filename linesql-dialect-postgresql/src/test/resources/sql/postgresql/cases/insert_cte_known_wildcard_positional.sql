WITH q AS (
    SELECT o.order_id AS id, o.total AS amount FROM sales.orders o
)
INSERT INTO mart.matches (match_id, amount)
SELECT q.* FROM q;

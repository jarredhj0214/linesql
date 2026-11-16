INSERT INTO mart.order_scores (order_id, adjusted)
SELECT o.id, q.adjusted
FROM sales.orders o
LEFT JOIN LATERAL (
    SELECT s.value + o.adjustment AS adjusted FROM sales.scores s WHERE s.id = o.id
) q ON TRUE;

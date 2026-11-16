WITH staged (payment, order_id) AS (
    SELECT (SELECT max(p.amount) FROM ods.payments AS p), a.id
    FROM ods.current_orders AS a
    UNION ALL
    SELECT b.amount, b.id FROM ods.archived_orders AS b
)
SELECT payment, order_id FROM staged ORDER BY order_id;

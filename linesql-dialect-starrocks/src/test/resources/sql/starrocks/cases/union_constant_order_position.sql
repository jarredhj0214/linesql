SELECT 0, a.amount AS amount FROM ods.current_orders AS a
UNION ALL
SELECT b.marker, b.total FROM ods.archived_orders AS b
ORDER BY 2;

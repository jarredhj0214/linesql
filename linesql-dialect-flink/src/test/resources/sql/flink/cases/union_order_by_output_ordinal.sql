SELECT 0 AS code, a.amount FROM ods.orders a
UNION ALL SELECT 1 AS code, b.total FROM ods.archived b
ORDER BY 2 LIMIT 10;

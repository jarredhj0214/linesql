WITH known AS (SELECT e.id, e.amount FROM ods.events e)
SELECT k.*, 5 AS batch_id, k.id + 1 AS next_id FROM known k ORDER BY 2, 4 LIMIT 10;

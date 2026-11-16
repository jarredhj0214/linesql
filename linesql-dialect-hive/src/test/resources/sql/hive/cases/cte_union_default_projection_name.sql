WITH staged AS (
    SELECT 0, a.amount AS amount FROM ods.current_orders a
    UNION ALL
    SELECT 1, b.total AS amount FROM ods.archived_orders b
)
SELECT _c0 AS batch_id, amount FROM staged ORDER BY amount;

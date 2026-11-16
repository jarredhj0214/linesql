WITH adjustments AS (
    SELECT order_id AS id, outer_adjustment AS adjustment FROM sales.adjustments
)
SELECT a.id, (SELECT MAX(a.adjustment) FROM sales.items a) AS item_adjustment
FROM adjustments a;

SELECT o.id, ROW_NUMBER() OVER ranked AS rn
FROM ods.orders o
WINDOW grouped AS (PARTITION BY o.customer_id),
       ranked AS (grouped ORDER BY o.created_at DESC)
QUALIFY rn <= 2;

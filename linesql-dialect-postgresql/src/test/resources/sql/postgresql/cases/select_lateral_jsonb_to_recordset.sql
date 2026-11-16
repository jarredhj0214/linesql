SELECT j.sku, j.qty
FROM ods.orders o
CROSS JOIN LATERAL jsonb_to_recordset(o.items) AS j(sku text, qty integer)
WHERE j.qty > 0;

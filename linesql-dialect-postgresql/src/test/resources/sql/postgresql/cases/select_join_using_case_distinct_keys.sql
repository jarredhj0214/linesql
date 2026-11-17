WITH orders (id, "ID") AS (SELECT order_id, version_id FROM ods.orders),
     invoices (id, "ID") AS (SELECT invoice_order_id, invoice_version FROM ops.invoices)
SELECT "Keys".ID AS canonical_id, "Keys"."ID" AS version_id
FROM orders LEFT JOIN invoices USING (ID, "ID") AS "Keys"
WHERE ID > 0
ORDER BY "Keys"."ID";

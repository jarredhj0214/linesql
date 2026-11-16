WITH shipments AS (
    SELECT external_id AS order_id, carrier FROM ods.shipments
)
SELECT o.order_id AS order_id, order_id AS merged_id, s.carrier
FROM ods.orders o RIGHT OUTER JOIN shipments s USING (order_id)
WHERE order_id > 0
ORDER BY order_id;

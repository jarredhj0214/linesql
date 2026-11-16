WITH shipped AS (
    SELECT external_id AS order_id, carrier FROM ods.shipments
)
SELECT order_id, s.carrier
FROM ods.orders o RIGHT OUTER JOIN shipped s USING (order_id)
ORDER BY order_id;

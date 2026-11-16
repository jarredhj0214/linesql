INSERT INTO mart.items (item_code, item_quantity, order_id)
SELECT f.*, o.id
FROM ods.orders AS o
CROSS JOIN LATERAL ROWS FROM (
    jsonb_array_elements_text(o.payload),
    unnest(o.quantities)
) AS f(code, quantity);

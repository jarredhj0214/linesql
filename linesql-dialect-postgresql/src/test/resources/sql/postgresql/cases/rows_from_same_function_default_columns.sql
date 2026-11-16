SELECT f.first_item, f.second_item
FROM ods.orders AS o
CROSS JOIN LATERAL ROWS FROM (
    jsonb_array_elements_text(o.payload),
    jsonb_array_elements_text(o.attributes)
) AS f(first_item, second_item);

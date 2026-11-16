SELECT json_array_elements.value, json_array_elements.unnest, json_array_elements.ordinality
FROM ods.orders AS o
CROSS JOIN LATERAL ROWS FROM (
    json_array_elements(o.payload),
    unnest(o.quantities)
) WITH ORDINALITY;

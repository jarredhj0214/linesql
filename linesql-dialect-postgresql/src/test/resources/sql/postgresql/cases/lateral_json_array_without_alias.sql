SELECT jsonb_array_elements.value
FROM ods.orders AS o
CROSS JOIN LATERAL jsonb_array_elements(o.payload);

SELECT JSON_VALUE(e.payload, '$.sku' RETURNING VARCHAR2(100 CHAR)) AS sku
FROM ods.events e;

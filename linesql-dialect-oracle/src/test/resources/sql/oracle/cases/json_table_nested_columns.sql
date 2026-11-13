SELECT e.event_id, j.sku, j.ord
FROM ods.events e
CROSS JOIN JSON_TABLE(e.payload, '$.orders[*]' COLUMNS (
    ord FOR ORDINALITY,
    NESTED PATH '$.items[*]' COLUMNS (sku varchar2(20) PATH '$.sku')
)) j
WHERE j.sku IS NOT NULL;

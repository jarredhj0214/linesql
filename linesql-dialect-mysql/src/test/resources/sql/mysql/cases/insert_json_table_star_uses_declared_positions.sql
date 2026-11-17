INSERT INTO audit.items(item_id, amount, event_id)
SELECT j.*, e.event_id
FROM ods.events e, JSON_TABLE(e.payload, '$.items[*]' COLUMNS (
    id BIGINT PATH '$.id', amount DECIMAL(10, 2) PATH '$.amount'
)) AS j;

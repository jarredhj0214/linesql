SELECT JSON_EXISTS(e.payload::jsonb, e.selector UNKNOWN ON ERROR) AS scalar_check,
       jt.has_item
FROM ods.events e
CROSS JOIN LATERAL JSON_TABLE(e.payload::jsonb, '$' COLUMNS (
    has_item boolean EXISTS PATH '$.item' UNKNOWN ON ERROR
)) jt
WHERE jt.has_item IS NOT UNKNOWN;

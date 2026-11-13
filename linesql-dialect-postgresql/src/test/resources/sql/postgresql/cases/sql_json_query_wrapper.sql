SELECT JSON_QUERY(e.payload, '$.items[*]'
                  RETURNING jsonb WITH CONDITIONAL ARRAY WRAPPER
                  DEFAULT e.fallback_items ON EMPTY EMPTY ARRAY ON ERROR) AS items
FROM ods.events AS e;

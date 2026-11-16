INSERT INTO archive.snapshots (payload)
SELECT JSON_OBJECTAGG(KEY e.event_key VALUE e.payload FORMAT JSON
                      ABSENT ON NULL RETURNING CLOB STRICT WITH UNIQUE KEYS)
       FILTER (WHERE e.is_visible = 1)
FROM app.events e;

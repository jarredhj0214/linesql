INSERT INTO mart.event_docs (document)
SELECT JSON_OBJECT(e.field_name VALUE e.payload FORMAT JSON, 'id':e.id
                   ABSENT ON NULL WITH UNIQUE KEYS RETURNING jsonb) AS document
FROM ods.events AS e
WHERE e.active;

INSERT INTO archive.documents (snapshot)
SELECT json_objectagg(e.event_key : e.payload FORMAT JSON
                      ABSENT ON NULL WITH UNIQUE KEYS RETURNING jsonb)
FROM app.events e;

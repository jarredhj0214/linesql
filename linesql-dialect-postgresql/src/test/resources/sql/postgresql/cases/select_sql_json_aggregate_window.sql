SELECT json_arrayagg(e.payload)
           FILTER (WHERE e.visible) OVER event_window AS events,
       json_objectagg(e.event_key VALUE e.payload NULL ON NULL RETURNING jsonb)
           OVER (PARTITION BY e.channel) AS event_map
FROM app.events e
WINDOW event_window AS (PARTITION BY e.channel ORDER BY e.seq);

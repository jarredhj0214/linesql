SELECT e.channel,
       json_arrayagg(e.payload ORDER BY e.seq DESC ABSENT ON NULL RETURNING jsonb)
           FILTER (WHERE e.visible) AS events
FROM app.events e
GROUP BY e.channel;

SELECT e.channel,
       JSON_ARRAYAGG(e.payload FORMAT JSON ORDER BY e.seq DESC
                     NULL ON NULL RETURNING VARCHAR2(1000 CHAR) STRICT)
           FILTER (WHERE e.is_visible = 1) AS event_array
FROM app.events e
GROUP BY e.channel;

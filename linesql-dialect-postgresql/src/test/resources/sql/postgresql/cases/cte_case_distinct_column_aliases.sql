WITH source (EVENT_ID, "Event_ID", "current_date") AS (
    SELECT id, version_id, "current_date" FROM ods.events
)
SELECT q.event_id AS display, q."Event_ID" AS "Display", q."current_date" AS observed_date
FROM source AS q
WHERE q.EVENT_ID > 0
ORDER BY DISPLAY, "Display";

SELECT e.id
FROM ods.events e
WHERE e.active = true
ORDER BY e.event_time DESC NULLS FIRST, e.id ASC NULLS LAST;

TABLE live.events UNION ALL SELECT b.id, b.event_time FROM archive.events b ORDER BY 1;

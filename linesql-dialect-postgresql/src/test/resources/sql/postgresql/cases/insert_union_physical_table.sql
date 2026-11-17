INSERT INTO analytics.snapshot(id, event_time)
TABLE live.events UNION ALL SELECT b.id, b.event_time FROM archive.events b;

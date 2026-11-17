SELECT a.id, a.*, a.note FROM live.events a
UNION ALL SELECT b.id, b.name, b.event_date, b.note FROM history.events b ORDER BY 1, 2;

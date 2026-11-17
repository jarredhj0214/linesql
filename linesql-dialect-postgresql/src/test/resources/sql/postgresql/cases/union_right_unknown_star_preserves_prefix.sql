SELECT b.id, b.name, b.event_date, b.note FROM history.events b
UNION ALL SELECT a.id, a.*, a.note FROM live.events a ORDER BY 1, 2;

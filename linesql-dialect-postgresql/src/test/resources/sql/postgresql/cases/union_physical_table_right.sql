SELECT b.id, b.event_time FROM archive.events b UNION ALL TABLE ONLY live.events ORDER BY 1;

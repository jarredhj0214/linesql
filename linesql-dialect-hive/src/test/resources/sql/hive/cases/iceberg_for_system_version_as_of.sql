SELECT v.id, c.status
FROM mart.events FOR SYSTEM_VERSION AS OF 3369973735913135680 AS v
JOIN ods.current_events AS c ON v.id = c.id
WHERE v.status <> c.status;

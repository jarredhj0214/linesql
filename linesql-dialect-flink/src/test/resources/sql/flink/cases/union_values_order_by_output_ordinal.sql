SELECT e.id, e.kind FROM ods.events e
UNION ALL VALUES (42, 0), (43, 1)
ORDER BY 2, 1;

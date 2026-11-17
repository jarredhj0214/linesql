(SELECT l.id FROM ods.live l)
UNION ALL
((SELECT h.id FROM ods.history h) UNION ALL VALUES (5))
ORDER BY 1;

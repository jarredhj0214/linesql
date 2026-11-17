(SELECT l.id FROM ods.live l UNION ALL SELECT h.id FROM ods.history h)
ORDER BY 1;

SELECT JSON(e.payload) FROM ods.events AS e
UNION ALL
SELECT JSON(h.document) FROM ods.events_history AS h
ORDER BY json;

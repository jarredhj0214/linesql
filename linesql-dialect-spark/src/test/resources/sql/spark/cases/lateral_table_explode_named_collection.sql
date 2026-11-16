SELECT item.pos, item.value
FROM ods.events AS e
CROSS JOIN LATERAL posexplode_outer(collection => e.items) AS item(pos, value)
WHERE item.value <> '';

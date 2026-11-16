SELECT item.tag, item.qty, item.pos
FROM ods.events e CROSS JOIN LATERAL pg_catalog.unnest(e.tags, e.quantities)
WITH ORDINALITY AS item(tag, qty, pos)
WHERE item.qty > 0;

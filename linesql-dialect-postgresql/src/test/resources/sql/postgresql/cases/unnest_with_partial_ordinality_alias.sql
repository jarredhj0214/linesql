SELECT item.tag, item.ordinality AS position_number
FROM ods.events e CROSS JOIN LATERAL unnest(e.tags) WITH ORDINALITY AS item(tag)
WHERE item.tag <> 'archived' AND item.ordinality > 1;

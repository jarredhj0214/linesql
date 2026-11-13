SELECT e.id, j.sku, j.qty, j.ord
FROM ods.events AS e
CROSS JOIN JSON_TABLE(
    e.payload, '$.orders[*]'
    COLUMNS (
        ord FOR ORDINALITY,
        sku text PATH '$.sku',
        NESTED PATH '$.items[*]' COLUMNS (
            qty integer PATH '$.quantity' DEFAULT 0 ON EMPTY
        )
    )
) AS j
WHERE j.qty > 0;

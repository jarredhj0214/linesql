DELETE FROM ONLY mart.inventory t
USING staging.stock a RIGHT JOIN staging.legacy_stock b USING (stock_id) AS merged
WHERE t.item_id = stock_id
RETURNING t.item_id, merged.stock_id AS source_id;

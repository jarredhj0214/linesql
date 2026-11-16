UPDATE mart.inventory t
SET source_id = stock_id
FROM (SELECT s.source_key FROM staging.stock s) AS a(stock_id)
FULL JOIN staging.legacy_stock b USING (stock_id)
WHERE t.item_id = stock_id;

UPDATE ONLY mart.inventory i
SET (status, quantity) = (VALUES ('active', s.quantity))
FROM staging.stock s
WHERE i.id = s.item_id;

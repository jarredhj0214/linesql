UPDATE ONLY mart.inventory i
SET (quantity, state) = (
    SELECT s.quantity + 1, s.state FROM staging.stock s WHERE i.id = s.item_id
    UNION ALL
    SELECT a.quantity + 1, a.state FROM archive.stock a WHERE i.id = a.item_id
);

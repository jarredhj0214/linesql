UPDATE mart.inventory t
SET (quantity, state, recorded_at) = ROW(s.quantity, DEFAULT, s.changed_at)
FROM staging.stock s
WHERE s.item_id = t.id;

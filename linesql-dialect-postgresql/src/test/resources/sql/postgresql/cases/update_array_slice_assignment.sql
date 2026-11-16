UPDATE mart.inventory t
SET counts[s.low_index:s.high_index] = s.new_counts
FROM staging.stock s
WHERE t.id = s.item_id;

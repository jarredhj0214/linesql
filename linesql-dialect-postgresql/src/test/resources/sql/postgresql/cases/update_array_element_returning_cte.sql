WITH updated AS (
    UPDATE mart.inventory t
    SET counts[s.bucket] = s.amount
    FROM staging.stock s
    WHERE t.id = s.item_id
    RETURNING WITH (NEW AS n) n.counts AS updated_counts
)
SELECT updated_counts FROM updated;

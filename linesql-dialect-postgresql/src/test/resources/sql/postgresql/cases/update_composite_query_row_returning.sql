WITH changed AS (
    UPDATE ONLY mart.orders o SET (shipping_address.city, region) = (
        SELECT l.city, l.region FROM staging.locations l WHERE l.order_id = o.id
    )
    RETURNING WITH (NEW AS n) n.shipping_address AS updated_address
)
SELECT updated_address FROM changed;

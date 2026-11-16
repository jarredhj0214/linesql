UPDATE ONLY mart.orders o
SET (shipping_address.city, previous_addresses[l.position].postal_code, region) =
    ROW(l.city, l.postal_code, l.region)
FROM staging.locations l
WHERE o.id = l.order_id;

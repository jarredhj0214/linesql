UPDATE ONLY mart.orders o
SET shipping_address.city = l.city
FROM staging.locations l
WHERE o.id = l.order_id;

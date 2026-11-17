DELETE FROM lake.mart.orders
WHERE EXISTS (
    SELECT 1
    FROM lake.staging.orders
    WHERE lake.staging.orders.id = lake.mart.orders.id
)
AND lake.mart.orders.expired;

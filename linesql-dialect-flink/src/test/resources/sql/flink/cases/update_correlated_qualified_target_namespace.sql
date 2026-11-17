UPDATE lake.mart.orders
SET amount = (
    SELECT max(lake.staging.orders.amount)
    FROM lake.staging.orders
    WHERE lake.staging.orders.id = lake.mart.orders.id
)
WHERE lake.mart.orders.active;

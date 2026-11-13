UPDATE dwd.orders AS o
SET status = s.status
FROM staging.order_status AS s
WHERE o.external_id <=> s.external_id;

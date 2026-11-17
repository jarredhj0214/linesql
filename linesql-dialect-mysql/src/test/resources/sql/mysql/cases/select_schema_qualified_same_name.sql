SELECT live.orders.id AS live_id, archive.orders.id AS archive_id
FROM live.orders JOIN archive.orders ON live.orders.customer_id = archive.orders.customer_id
WHERE live.orders.enabled <> archive.orders.enabled ORDER BY archive.orders.ordered_at;

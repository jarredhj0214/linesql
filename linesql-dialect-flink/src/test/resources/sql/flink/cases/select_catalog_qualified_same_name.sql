SELECT lake.live.orders.id AS live_id, archive.history.orders.id AS history_id
FROM lake.live.orders JOIN archive.history.orders
  ON lake.live.orders.link_id = archive.history.orders.link_id
WHERE lake.live.orders.status = 1;

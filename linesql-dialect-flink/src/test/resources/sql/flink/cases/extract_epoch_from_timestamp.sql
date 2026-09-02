SELECT
  EXTRACT(EPOCH FROM event_time) AS event_epoch
FROM dwd.events

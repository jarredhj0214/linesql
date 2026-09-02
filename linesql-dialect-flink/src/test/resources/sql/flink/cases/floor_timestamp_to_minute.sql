SELECT
  FLOOR(event_time TO MINUTE) AS event_minute
FROM dwd.events
